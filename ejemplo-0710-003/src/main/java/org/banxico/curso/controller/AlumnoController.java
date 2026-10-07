package org.banxico.curso.controller;

import java.util.List;
import java.util.Optional;

import org.banxico.curso.entity.Alumno;
import org.banxico.curso.repository.AlumnoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import lombok.AllArgsConstructor;

@RestController
@RequestMapping("/alumnos")
@AllArgsConstructor
public class AlumnoController {

    private final AlumnoRepository alumnoRepository;

    @GetMapping
    public List<Alumno> obtenerAlumnos() {
        return alumnoRepository.findAll();
    }

    @PostMapping
    public ResponseEntity<Alumno> crearAlumno(@RequestBody Alumno alumno) {
        Optional<Alumno> alumnoExistente = alumnoRepository.findByNombre(alumno.getNombre());

        if (alumnoExistente.isPresent()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "El alumno con nombre " + alumno.getNombre() + " ya está registrado.");
        }

        Alumno alumnoCreado = alumnoRepository.save(alumno);
        return new ResponseEntity<>(alumnoCreado, HttpStatus.CREATED);
    }
}
