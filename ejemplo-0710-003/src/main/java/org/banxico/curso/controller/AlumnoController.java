package org.banxico.curso.controller;

import java.util.List;

import org.banxico.curso.entity.Alumno;
import org.banxico.curso.repository.AlumnoRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
    public Alumno crearAlumno(@RequestBody Alumno alumno) {
        Alumno alumnoCreado = alumnoRepository.save(alumno);

        return alumnoCreado;
    }
}
