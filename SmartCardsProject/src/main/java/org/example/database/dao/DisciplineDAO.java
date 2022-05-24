package org.example.database.dao;

import org.example.database.entities.Disciplina;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import javax.transaction.Transactional;

@Repository
@Transactional
public interface DisciplineDAO extends JpaRepository<Disciplina, Integer> {
}
