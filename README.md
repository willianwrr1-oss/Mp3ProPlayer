# MP3 Pro Player

Player de MP3 para Android 13 ou superior que toca a pasta do OneDrive compartilhada por link (sem login), em streaming ou offline.

## O que o app faz

- Lista as músicas da pasta do OneDrive e navega por subpastas, se você criar alguma no futuro.
- Aba "Celular": encontra as músicas do aparelho (memória interna e cartão SD), mostra as pastas para você navegar e toca uma pasta inteira, com subpastas. Pede a permissão "Música e áudio" na primeira vez.
- Toca mp3, m4a, aac, flac, wav, ogg, opus, amr, mka, weba, ac3, eac3 e 3gp. Formatos que o Android não decodifica, como wma e midi, não aparecem.
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

## Atualizar o app por cima

O projeto traz uma chave de assinatura fixa (`app/mp3pro.jks`), e o número da versão sobe sozinho a cada compilação no GitHub. Assim, os próximos APKs instalam por cima do anterior, mantendo os ajustes do equalizador e as músicas baixadas.

Na primeira vez com esta chave, desinstale a versão antiga do celular (ela foi assinada com outra chave) e instale a nova. Depois disso, é só instalar por cima.

A chave fica dentro do repositório e serve apenas para instalar o app direto no seu celular. Ela não serve para publicar na Play Store.

## Link da pasta do OneDrive

O link padrão está em `app/src/main/java/com/willian/mp3pro/data/AppPrefs.kt`. Dá para trocá-lo sem recompilar: na Biblioteca, toque na engrenagem, cole o novo link e salve.

Se o repositório for público, esse link ficará visível no código. Como a pasta já é aberta a quem tiver o link, isso só importa se você quiser manter o link reservado; nesse caso deixe o repositório privado.

## Se a pasta não carregar

A leitura anônima de pastas do OneDrive não é um recurso documentado pela Microsoft. Para links novos (`1drv.ms/f/c/...`), o app usa o mesmo token de visitante que o navegador usa para quem abre o link sem login; para links no formato antigo, usa o endereço `api.onedrive.com` como reserva. Se todos os caminhos falharem, a tela de erro mostra "Detalhes técnicos". Tire um print dessa tela e envie na conversa: com ela dá para ajustar só o módulo `OneDriveClient.kt`.

Por ser um recurso não documentado, a Microsoft pode mudá-lo no futuro. O plano B, nesse caso, é registrar o app no Azure e entrar com login Microsoft.

Os links de download do OneDrive expiram em cerca de uma hora, por isso o app pede um link novo na hora de tocar ou baixar cada música.

## Estrutura

- `audio/`: equalizador, efeitos e persistência dos ajustes (`AudioFxProcessor.kt` é o motor de áudio).
- `data/`: leitura do OneDrive, downloads e armazenamento offline.
- `player/`: serviço de reprodução em segundo plano (Media3) e o controlador usado pela tela.
- `ui/`: telas em Jetpack Compose.
