package skillfactory.luzhin.booking.exception;

/**
 * Ошибка обращения к отсутствующему ресурсу.
 */
public class ResourceNotFoundException extends RuntimeException {

    /**
     * Создаёт ошибку с указанием типа и идентификатора ресурса.
     *
     * @param resourceName название ресурса
     * @param id идентификатор ресурса
     */
    public ResourceNotFoundException(String resourceName, Long id) {
        super("%s with id %d was not found".formatted(resourceName, id));
    }
}
