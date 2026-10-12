const express = require('express');

const app = express();
const PORT = process.env.PORT || 3000;  

app.use(express.json());

app.get("/", (req, res) => {
    res.json({
        message: "Hola desde Express",
    });
});

app.get("/hostname", (req, res) => {
    res.send(`Nombre del host: ${process.env.HOSTNAME}`);
})

app.get("/v4", (req, res) => {
    res.json({
        message: "Bienvenido a la version 4 del API, vamos a probar un rollback con el deployment"
    })
})

app.listen(PORT, () => {
    console.log(`Servidor corriendo en http://localhost:${PORT}`);
});