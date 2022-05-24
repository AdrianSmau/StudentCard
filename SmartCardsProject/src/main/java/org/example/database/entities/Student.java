package org.example.database.entities;

import lombok.*;

import javax.persistence.*;

@Entity
@Table(name = "studenti")
@Setter
@Getter
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
public class Student {
    @Id
    private Integer id;
    private String name;
    private boolean reexaminarePlatita;
}
