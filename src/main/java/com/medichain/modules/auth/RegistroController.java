package com.medichain.modules.auth;

import com.medichain.modules.empresa.Empresa;
import com.medichain.modules.empresa.EmpresaService;
import com.medichain.modules.empresa.RegistroEmpresaRequestDTO;
import com.medichain.modules.usuario.RegistroPacienteRequestDTO;
import com.medichain.modules.usuario.Usuario;
import com.medichain.modules.usuario.UsuarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Controlador RegistroController en MediChain.
 * Registro PÚBLICO (sin token): empresas con su administrador inicial y
 * pacientes. Responde 201 con un identificador legible y el estado, sin
 * ids internos. Protecciones: Bean Validation, PDF real de hasta 5 MB,
 * mensajes que no revelan si un email ya tiene cuenta.
 * TODO: rate limiting por IP (por ejemplo Bucket4j) en /api/registro/** y
 * /api/auth/login, captcha y verificación del email.
 */
@RestController
@RequestMapping("/api/registro")
@Tag(name = "Registro público", description = "Alta de empresas (con PDF de habilitación) y de pacientes, sin token")
public class RegistroController {

    private final EmpresaService empresaService;
    private final UsuarioService usuarioService;

    @Autowired
    public RegistroController(EmpresaService empresaService, UsuarioService usuarioService) {
        this.empresaService = empresaService;
        this.usuarioService = usuarioService;
    }

    /** Registra una empresa PENDIENTE, su administrador inicial y el PDF de habilitación. */
    @PostMapping(value = "/empresas", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Registrar una empresa", description = "Formulario multipart, no necesita token. Campos de la empresa (tipo, cuit, razonSocial, gln, provincia, localidad, domicilio; opcionales numeroHabilitacion y directorTecnico), del administrador inicial (adminEmail, adminPassword, adminNombre, adminApellido, adminDni; adminEsDirectorTecnico solo para LABORATORIO) y documento: el PDF (hasta 5 MB). Respuestas: 201 {identificador = CUIT, estado = PENDIENTE}; 400 con el campo que falló; 409 EMPRESA_DUPLICADA (CUIT o GLN ya registrado) o REGISTRO_NO_COMPLETADO (email). Queda en la bandeja de los inspectores de su provincia. Evento SOLICITUD_HABILITACION.")
    public ResponseEntity<RegistroResponseDTO> registrarEmpresa(@Valid @ModelAttribute RegistroEmpresaRequestDTO dto) {
        byte[] pdf;
        try {
            pdf = dto.getDocumento().getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el documento subido", e);
        }
        Empresa empresa = empresaService.registrar(dto, pdf, dto.getDocumento().getOriginalFilename());
        return ResponseEntity.status(HttpStatus.CREATED).body(respuesta(empresa.getCuit(), empresa.getEstado().name(),
                "Solicitud recibida: queda pendiente de revisión por un inspector de ANMAT"));
    }

    /** Registra un paciente. */
    @PostMapping("/pacientes")
    @Operation(summary = "Registrar un paciente", description = "Crea una cuenta PACIENTE activa (después hay que hacer login). Público.")
    public ResponseEntity<RegistroResponseDTO> registrarPaciente(@Valid @RequestBody RegistroPacienteRequestDTO dto) {
        Usuario paciente = usuarioService.registrarPaciente(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(respuesta(paciente.getEmail(), "ACTIVO",
                "Cuenta creada: ya podés iniciar sesión"));
    }

    /** Arma la respuesta pública del registro. */
    private RegistroResponseDTO respuesta(String identificador, String estado, String mensaje) {
        RegistroResponseDTO dto = new RegistroResponseDTO();
        dto.setIdentificador(identificador);
        dto.setEstado(estado);
        dto.setMensaje(mensaje);
        return dto;
    }
}
