package skillfactory.luzhin.booking.exception;

/**
 * Ошибка нарушения бизнес-ограничения или целостности данных.
 */
public class BusinessConflictException extends RuntimeException {

    /**
     * Создаёт ошибку конфликта с указанным описанием.
     *
     * @param message описание конфликта
     */
    public BusinessConflictException(String message) {
        super(message);
    }
}
