package org.example.database.dao;

import org.example.database.entities.Nota;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import javax.transaction.Transactional;
import java.util.List;

@Repository
@Transactional
public interface NoteDAO extends JpaRepository<Nota, Integer> {
    List<Nota> findByStudentIdAndDisciplinaId(int studentId, int disciplinaId);
}
