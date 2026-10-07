# Imagen base que tiene herramientas Linux que vamos a utilizar
FROM alpine

COPY holaMundo.sh /
RUN chmod +x /holaMundo.sh

ENTRYPOINT ["/holaMundo.sh", "Hola Docker"]
CMD ["--port", "8080"]