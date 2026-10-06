// SPDX-License-Identifier: MIT
// ↑ Licencia del código fuente. Etherscan la pide al publicar (verificar) el contrato.

pragma solidity 0.8.37;
// ↑ Versión EXACTA del compilador (sin "^"): cualquiera puede recompilar este archivo
//   con la misma versión y configuración y obtener byte a byte el código desplegado.

/// @title MediChainAnchor
/// @notice Registro público e inmutable de los hashes de la cadena de eventos de MediChain.
/// @dev Solo el dueño (la billetera que lo desplegó) escribe; cualquiera lee.
///      No hay función para borrar, corregir ni reiniciar anclajes: por diseño.
contract MediChainAnchor {

    // Cuenta que desplegó el contrato. "immutable": se fija una sola vez en el constructor
    // y queda grabada en el código del contrato; nadie puede cambiarla (no hay cambio de dueño).
    // "public" genera automáticamente la función de lectura duenio().
    address public immutable duenio;

    // Hash anclado por número de evento: hashPorNumero[84] = hash del evento #84.
    // Un número que nunca se ancló devuelve 0x00…00.
    mapping(uint64 => bytes32) private hashPorNumero;

    // Números anclados, en el orden en que se anclaron (siempre crecientes).
    // Permite recorrer TODOS los anclajes desde la blockchain, sin depender de la base de MediChain.
    uint64[] private numerosAnclados;

    // Constancia pública de cada anclaje: queda en el recibo de la transacción
    // (Etherscan, pestaña "Logs"). "indexed" permite buscar por número de evento.
    // fecha = hora del bloque en segundos desde 1970 (UTC).
    event Anclado(uint64 indexed hastaNumero, bytes32 hash, uint256 fecha);

    // Errores con nombre: más baratos que un mensaje de texto y Etherscan los muestra legibles.
    error NoEsElDuenio(address llamador);                    // alguien que no es el dueño intentó anclar
    error NumeroNoCreciente(uint64 recibido, uint64 ultimo); // número repetido o menor al último anclado
    error HashVacio();                                       // un hash en cero no representa ningún evento

    // Se ejecuta UNA sola vez, al desplegar: la cuenta que firma el despliegue queda como dueña.
    constructor() {
        duenio = msg.sender;
    }

    /// @notice Ancla el hash del evento número `hastaNumero`. Como cada evento incluye el hash
    ///         del anterior, ese único hash protege a todos los eventos 1..hastaNumero.
    /// @param hash        hash SHA-256 del evento (64 hexadecimales en MediChain = 32 bytes).
    /// @param hastaNumero número del evento cuyo hash se ancla.
    function anclar(bytes32 hash, uint64 hastaNumero) external {
        // 1. Solo la billetera de MediChain puede anclar.
        if (msg.sender != duenio) revert NoEsElDuenio(msg.sender);
        // 2. Un hash en cero no es un evento: se rechaza.
        if (hash == bytes32(0)) revert HashVacio();
        // 3. Estrictamente creciente: un anclaje pasado no se puede reescribir ni repetir.
        uint64 ultimo = ultimoNumero();
        if (hastaNumero <= ultimo) revert NumeroNoCreciente(hastaNumero, ultimo);
        // 4. Guarda el hash y agrega el número al final de la lista.
        hashPorNumero[hastaNumero] = hash;
        numerosAnclados.push(hastaNumero);
        // 5. Deja constancia pública con la fecha del bloque.
        emit Anclado(hastaNumero, hash, block.timestamp);
    }

    /// @notice Hash anclado para un número de evento (0x00…00 si ese número no se ancló).
    function hashAnclado(uint64 numero) external view returns (bytes32) {
        return hashPorNumero[numero];
    }

    /// @notice true si `hash` es exactamente el hash anclado para `numero`.
    function verificar(uint64 numero, bytes32 hash) external view returns (bool) {
        return hash != bytes32(0) && hashPorNumero[numero] == hash;
    }

    /// @notice Último número anclado (0 si todavía no hay anclajes).
    function ultimoNumero() public view returns (uint64) {
        uint256 cantidad = numerosAnclados.length;
        return cantidad == 0 ? 0 : numerosAnclados[cantidad - 1];
    }

    /// @notice Cantidad de anclajes realizados.
    function cantidadAnclajes() external view returns (uint256) {
        return numerosAnclados.length;
    }

    /// @notice Una página de anclajes: posiciones [desde, desde + cantidad) de la lista, con sus
    ///         números y hashes. Permite auditar todos los anclajes leyendo solo la blockchain.
    function anclajes(uint256 desde, uint256 cantidad)
        external
        view
        returns (uint64[] memory numeros, bytes32[] memory hashes)
    {
        uint256 total = numerosAnclados.length;
        if (desde > total) desde = total;                       // fuera de rango: página vacía
        if (cantidad > total - desde) cantidad = total - desde; // recorta al final, sin desbordar
        numeros = new uint64[](cantidad);
        hashes = new bytes32[](cantidad);
        for (uint256 i = 0; i < cantidad; i++) {
            numeros[i] = numerosAnclados[desde + i];
            hashes[i] = hashPorNumero[numeros[i]];
        }
    }
}
