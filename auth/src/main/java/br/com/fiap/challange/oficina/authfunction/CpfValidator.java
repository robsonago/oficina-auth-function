package br.com.fiap.challange.oficina.authfunction;

/**
 * Mesma regra de validação de CPF usada em CpfCnpjValidator (repositório
 * oficina) — reimplementada aqui porque esta function é um artefato Maven
 * independente, sem acesso ao módulo da aplicação principal.
 */
final class CpfValidator {

    private CpfValidator() {
    }

    static String normalizar(String documento) {
        if (documento == null) return null;
        return documento.replaceAll("[^0-9]", "");
    }

    static boolean isValido(String documento) {
        String digits = normalizar(documento);
        if (digits == null || digits.length() != 11) return false;
        if (digits.chars().distinct().count() == 1) return false;

        int soma = 0;
        for (int i = 0; i < 9; i++) soma += Character.getNumericValue(digits.charAt(i)) * (10 - i);
        int r1 = (soma * 10) % 11;
        if (r1 == 10 || r1 == 11) r1 = 0;
        if (r1 != Character.getNumericValue(digits.charAt(9))) return false;

        soma = 0;
        for (int i = 0; i < 10; i++) soma += Character.getNumericValue(digits.charAt(i)) * (11 - i);
        int r2 = (soma * 10) % 11;
        if (r2 == 10 || r2 == 11) r2 = 0;
        return r2 == Character.getNumericValue(digits.charAt(10));
    }
}
