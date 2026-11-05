package skillfactory.luzhin.booking.exception;

/**
 * Ошибка некорректных бизнес-параметров запроса.
 */
public class InvalidRequestException extends RuntimeException {

    /**
     * Создаёт ошибку с указанным описанием.
     *
     * @param message описание ошибки
     */
    public InvalidRequestException(String message) {
        super(message);
    }
}
