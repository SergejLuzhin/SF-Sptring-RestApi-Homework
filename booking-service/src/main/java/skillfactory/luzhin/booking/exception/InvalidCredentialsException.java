package skillfactory.luzhin.booking.exception;

/**
 * Ошибка неверных учётных данных при входе.
 */
public class InvalidCredentialsException extends RuntimeException {

    /**
     * Создаёт ошибку без раскрытия причины отказа.
     */
    public InvalidCredentialsException() {
        super("Invalid username or password");
    }
}
