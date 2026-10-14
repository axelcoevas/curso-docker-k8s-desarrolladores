IF DB_ID('curso') IS NULL
    CREATE DATABASE curso;
GO

USE curso;
GO

-- Misma estructura que genera Hibernate para la entidad Alumno (ejemplo-0710-003)
IF OBJECT_ID('dbo.alumnos', 'U') IS NULL
    CREATE TABLE dbo.alumnos (
        id BIGINT IDENTITY NOT NULL PRIMARY KEY,
        nombre VARCHAR(255)
    );
GO

-- Solo inserta si la tabla está vacía, para que el script se pueda ejecutar más de una vez
IF NOT EXISTS (SELECT 1 FROM dbo.alumnos)
    INSERT INTO dbo.alumnos (nombre) VALUES
        ('Andrea'),
        ('Alexis'),
        ('Asher'),
        ('Axel'),
        ('Fernando'),
        ('Jorge'),
        ('Levi'),
        ('Humberto');
GO

SELECT * FROM dbo.alumnos