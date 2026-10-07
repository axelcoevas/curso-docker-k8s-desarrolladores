package org.banxico.curso.repository;

import java.util.Optional;

import org.banxico.curso.entity.Alumno;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlumnoRepository extends JpaRepository<Alumno, Long> {
    public Optional<Alumno> findByNombre(String nombre);
}
