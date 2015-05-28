package models;
 
import javax.persistence.*;

 
/**
 * Base entity for all JPA classes
 */
@MappedSuperclass
public class BaseEntity {
 
    @Id
    @GeneratedValue
    protected Long id;
 
    public Long getId() {
        return id;
    }
 
	
}