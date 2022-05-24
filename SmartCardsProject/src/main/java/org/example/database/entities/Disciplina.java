package org.example.database.entities;

import lombok.*;

import javax.persistence.*;

@Entity
@Table(name = "discipline")
@Setter
@Getter
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
public class Disciplina {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    private String name;
}
