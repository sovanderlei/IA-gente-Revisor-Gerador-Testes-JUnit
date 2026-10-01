package com.example.service;

import com.example.model.User;
import com.example.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService")
class UserServiceTest {

    @Mock
    private UserRepository repository;

    @InjectMocks
    private UserService userService;

    @Nested
    @DisplayName("findById")
    class FindById {

        @Test
        @DisplayName("Retorna o usuário quando o id existe")
        void should_returnUser_when_idExists() {
            // Arrange
            Long id = 1L;
            User expected = mock(User.class);
            when(repository.findById(id)).thenReturn(expected);

            // Act
            User result = userService.findById(id);

            // Assert
            assertNotNull(result);
            assertSame(expected, result);
            verify(repository).findById(id);
            verifyNoMoreInteractions(repository);
        }

        @Test
        @DisplayName("Retorna null quando o id não existe")
        void should_returnNull_when_idDoesNotExist() {
            // Arrange
            Long id = 999L;
            when(repository.findById(id)).thenReturn(null);

            // Act
            User result = userService.findById(id);

            // Assert
            assertNull(result);
            verify(repository).findById(id);
        }

        @Test
        @DisplayName("Repassa id nulo ao repositório (ausência de validação)")
        void should_delegateNullId_when_idIsNull() {
            // Arrange
            when(repository.findById(null)).thenReturn(null);

            // Act
            User result = userService.findById(null);

            // Assert
            assertNull(result);
            verify(repository).findById(null);
        }

        @Test
        @DisplayName("Aceita ids limite (0 e negativos) sem validação")
        void should_delegateBoundaryIds_when_idIsZeroOrNegative() {
            // Arrange
            when(repository.findById(0L)).thenReturn(null);
            when(repository.findById(-1L)).thenReturn(null);

            // Act
            User zero = userService.findById(0L);
            User negative = userService.findById(-1L);

            // Assert
            assertNull(zero);
            assertNull(negative);
            verify(repository).findById(0L);
            verify(repository).findById(-1L);
        }

        @Test
        @DisplayName("Propaga exceção do repositório")
        void should_propagateException_when_repositoryFails() {
            // Arrange
            when(repository.findById(1L)).thenThrow(new IllegalStateException("db offline"));

            // Act
            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> userService.findById(1L));

            // Assert
            assertEquals("db offline", ex.getMessage());
            verify(repository).findById(1L);
        }
    }

    @Nested
    @DisplayName("deleteUser")
    class DeleteUser {

        @Test
        @DisplayName("Remove o usuário quando o username é válido")
        void should_deleteUser_when_usernameIsValid() {
            // Arrange
            String username = "john.doe";

            // Act
            assertDoesNotThrow(() -> userService.deleteUser(username));

            // Assert
            verify(repository).deleteByUsername(username);
            verifyNoMoreInteractions(repository);
        }

        @Test
        @DisplayName("Repassa username nulo ao repositório (ausência de validação)")
        void should_delegateNullUsername_when_usernameIsNull() {
            // Act
            userService.deleteUser(null);

            // Assert
            verify(repository).deleteByUsername(null);
        }

        @Test
        @DisplayName("Repassa username vazio ao repositório (ausência de validação)")
        void should_delegateEmptyUsername_when_usernameIsEmpty() {
            // Act
            userService.deleteUser("");

            // Assert
            verify(repository).deleteByUsername("");
        }

        @Test
        @DisplayName("Propaga exceção do repositório ao remover")
        void should_propagateException_when_repositoryFailsOnDelete() {
            // Arrange
            doThrow(new IllegalStateException("violação de integridade"))
                    .when(repository).deleteByUsername(anyString());

            // Act + Assert
            assertThrows(IllegalStateException.class, () -> userService.deleteUser("john.doe"));
            verify(repository).deleteByUsername("john.doe");
        }

        @Test
        @DisplayName("Suporta remoções concorrentes sem perder chamadas")
        void should_invokeRepositoryForEachCall_when_calledConcurrently() throws InterruptedException {
            // Arrange
            int threads = 10;
            ExecutorService pool = Executors.newFixedThreadPool(threads);
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(threads);

            // Act
            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    try {
                        start.await();
                        userService.deleteUser("concurrent");
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            boolean finished = done.await(5, TimeUnit.SECONDS);
            pool.shutdown();

            // Assert
            assertTrue(finished);
            verify(repository, times(threads)).deleteByUsername("concurrent");
        }
    }
}
