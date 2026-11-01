package skillfactory.luzhin.hotel.exception;

/**
 * Ошибка некорректных бизнес-параметров запроса.
 */
public class InvalidRequestException extends RuntimeException {

    /**
     * Создаёт ошибку некорректного запроса.
     *
     * @param message описание ошибки
     */
    public InvalidRequestException(String message) {
        super(message);
    }
}
