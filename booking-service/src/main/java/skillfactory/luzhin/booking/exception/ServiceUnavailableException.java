package skillfactory.luzhin.booking.exception;

/**
 * Ошибка невозможности безопасно завершить операцию без удалённого сервиса.
 */
public class ServiceUnavailableException extends RuntimeException {

    /**
     * Создаёт ошибку с указанным описанием.
     *
     * @param message описание ошибки
     */
    public ServiceUnavailableException(String message) {
        super(message);
    }
}
