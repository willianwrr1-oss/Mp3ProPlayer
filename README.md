# MP3 Pro Player

Player de MP3 para Android 13 ou superior que toca a pasta do OneDrive compartilhada por link (sem login), em streaming ou offline.

## O que o app faz

- Lista as músicas da pasta do OneDrive e navega por subpastas, se você criar alguma no futuro.
- Toca em segundo plano, com notificação, controles na tela de bloqueio e fones Bluetooth.
- Fila com aleatório e repetição (tudo / uma música).
- Baixa músicas (uma a uma ou a pasta inteira) para ouvir sem internet; a aba "Baixadas" funciona sem conexão.
- Equalizador próprio de 6 faixas (60 Hz, 250 Hz, 1 kHz, 3 kHz, 8 kHz, 14 kHz, de -12 a +12 dB), com pré-amplificador e presets.
- Efeitos: Loudness, Graves profundos, Espacial (alargamento estéreo) e Clareza, mais o preset "Turbo (Loud + Surround)".

Os efeitos são do próprio app e não usam o nome nem o algoritmo SRS WOW, que é proprietário.

## Como gerar o APK (tudo pelo site do GitHub)

1. Descompacte o ZIP no computador.
2. No GitHub, crie um repositório novo (botão **New**).
3. Dentro do repositório, clique em **Add file > Upload files** e arraste todo o conteúdo da pasta descompactada (inclusive a pasta oculta `.github`). Clique em **Commit changes**.
4. Abra a aba **Actions**. O fluxo "Gerar APK" começa sozinho; se não começar, abra-o e clique em **Run workflow**.
5. Quando ficar verde (alguns minutos), abra a execução e baixe **Mp3ProPlayer-apk** em "Artifacts".
6. Descompacte esse arquivo, passe o `.apk` para o celular e instale (permita "instalar apps desconhecidos" quando o Android pedir).

Confira se o arquivo `.github/workflows/build-apk.yml` aparece no repositório; sem ele a aba Actions não tem o fluxo.

## Link da pasta do OneDrive

O link padrão está em `app/src/main/java/com/willian/mp3pro/data/AppPrefs.kt`. Dá para trocá-lo sem recompilar: na Biblioteca, toque na engrenagem, cole o novo link e salve.

Se o repositório for público, esse link ficará visível no código. Como a pasta já é aberta a quem tiver o link, isso só importa se você quiser manter o link reservado; nesse caso deixe o repositório privado.

## Se a pasta não carregar

A leitura anônima de pastas do OneDrive não é um recurso bem documentado pela Microsoft. O app tenta quatro caminhos de acesso e, se todos falharem, mostra "Detalhes técnicos" na tela de erro. Tire um print dessa tela e envie na conversa: com ela dá para ajustar só o módulo `OneDriveClient.kt`.

O plano B, se o acesso anônimo não funcionar no seu caso, é registrar o app no Azure e entrar com login Microsoft.

## Estrutura

- `audio/`: equalizador, efeitos e persistência dos ajustes (`AudioFxProcessor.kt` é o motor de áudio).
- `data/`: leitura do OneDrive, downloads e armazenamento offline.
- `player/`: serviço de reprodução em segundo plano (Media3) e o controlador usado pela tela.
- `ui/`: telas em Jetpack Compose.
