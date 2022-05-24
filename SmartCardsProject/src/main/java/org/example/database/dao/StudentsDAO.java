package org.example.database.dao;

import org.example.database.entities.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import javax.transaction.Transactional;
import java.util.Optional;

@Repository
@Transactional
public interface StudentsDAO extends JpaRepository<Student, Integer> {
    Optional<Student> findById(int id);
}
