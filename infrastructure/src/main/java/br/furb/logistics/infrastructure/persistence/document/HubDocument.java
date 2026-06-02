package br.furb.logistics.infrastructure.persistence.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document("hubs")
public class HubDocument {

    @Id
    private String id;
    private String name;
    @Indexed(unique = true)
    private String cep;
    private String city;
    private String state;
    private double latitude;
    private double longitude;
    private boolean active;
}
