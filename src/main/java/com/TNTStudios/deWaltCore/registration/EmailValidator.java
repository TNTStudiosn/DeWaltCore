// RUTA: src/main/java/com/TNTStudios/deWaltCore/registration/EmailValidator.java
package com.TNTStudios.deWaltCore.registration;

import java.util.regex.Pattern;

/**
 * Mi utilidad para validar el formato de correos electrónicos.
 * Ahora es una clase de utilidad estática para máxima eficiencia.
 */
public final class EmailValidator {

    // Uso una expresión regular (regex) estándar para una validación de formato robusta.
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,6}$");

    /**
     * Constructor privado para evitar que esta clase de utilidad sea instanciada.
     */
    private EmailValidator() {
        // No se debe instanciar.
    }

    /**
     * Comprueba si el correo tiene un formato válido (contiene '@', dominio, etc.).
     * @param email El correo a verificar.
     * @return true si el formato es correcto.
     */
    public static boolean isValidFormat(String email) {
        if (email == null) {
            return false;
        }
        return EMAIL_PATTERN.matcher(email).matches();
    }
}