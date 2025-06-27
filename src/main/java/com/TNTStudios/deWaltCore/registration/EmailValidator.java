// RUTA: src/main/java/com/TNTStudios/deWaltCore/registration/EmailValidator.java
package com.TNTStudios.deWaltCore.registration;

import com.TNTStudios.deWaltCore.DeWaltCore;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Mi utilidad para validar correos electrónicos.
 * Incluye una comprobación de formato y una validación web asíncrona.
 */
public class EmailValidator {

    private final DeWaltCore plugin;
    // Uso una expresión regular (regex) estándar para una validación de formato robusta.
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,6}$");

    // URL de un servicio de validación de correo hipotético/de ejemplo.
    // En un caso real, aquí iría la URL de la API que contratemos.
    private static final String VALIDATION_API_URL = "https://api.eva.pingutil.com/e-mail?email=";

    public EmailValidator(DeWaltCore plugin) {
        this.plugin = plugin;
    }

    /**
     * Comprueba si el correo tiene un formato válido (contiene '@', dominio, etc.).
     * @param email El correo a verificar.
     * @return true si el formato es correcto.
     */
    public boolean isValidFormat(String email) {
        if (email == null) {
            return false;
        }
        return EMAIL_PATTERN.matcher(email).matches();
    }

    /**
     * Verifica un correo usando una API externa de forma asíncrona.
     * @param email El correo a verificar.
     * @param callback La acción a ejecutar cuando se reciba la respuesta (true si es válido, false si no).
     */
    public void verifyEmailOnline(String email, Consumer<Boolean> callback) {
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    URL url = new URL(VALIDATION_API_URL + email);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(5000); // 5 segundos de timeout
                    conn.setReadTimeout(5000);

                    if (conn.getResponseCode() == 200) {
                        // Aquí leeríamos la respuesta de la API.
                        // Por ejemplo, si la API devuelve {"valid": true}, analizaríamos ese JSON.
                        // Para este ejemplo, simplemente asumiré que un código 200 significa que es válido.
                        // En un caso real, aquí iría la lógica de parsing del JSON.

                        // Ejemplo simple de lectura de respuesta
                        BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                        String inputLine;
                        StringBuilder content = new StringBuilder();
                        while ((inputLine = in.readLine()) != null) {
                            content.append(inputLine);
                        }
                        in.close();
                        conn.disconnect();

                        // Supongamos que la API contiene "is_valid":true en su respuesta si es correcto
                        boolean isValid = content.toString().contains("\"is_valid\":true");

                        // Devolvemos el control al hilo principal para ejecutar el callback de forma segura.
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                callback.accept(isValid);
                            }
                        }.runTask(plugin);

                    } else {
                        // Si la API da un error, consideramos el email como no válido.
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                callback.accept(false);
                            }
                        }.runTask(plugin);
                    }
                } catch (Exception e) {
                    // Si hay un error de conexión, también lo consideramos inválido.
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            callback.accept(false);
                        }
                    }.runTask(plugin);
                }
            }
        }.runTaskAsynchronously(plugin);
    }
}