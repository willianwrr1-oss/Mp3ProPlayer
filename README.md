# MP3 Pro Player

Player de MP3 para Android 13 ou superior que toca a pasta do OneDrive compartilhada por link (sem login), em streaming ou offline.

## O que o app faz

- Lista as músicas da pasta do OneDrive e navega por subpastas, se você criar alguma no futuro.
- Aba "Celular", botão "Escolher pasta do celular": abre o seletor de pastas do Android; você navega até a pasta das músicas, e ela fica salva em "Suas pastas" para abrir de novo. Dentro dela dá para entrar em subpastas e tocar a pasta inteira.
- Aba "Celular", mais abaixo: encontra as músicas do aparelho (memória interna e cartão SD), mostra as pastas para você navegar e toca uma pasta inteira, com subpastas. Pede a permissão "Música e áudio" na primeira vez.
- Toca mp3, m4a, aac, flac, wav, ogg, opus, amr, mka, weba, ac3, eac3 e 3gp. Formatos que o Android não decodifica, como wma e midi, não aparecem.
- Toca em segundo plano, com notificação, controles na tela de bloqueio e fones Bluetooth.
- Fila com aleatório e repetição (tudo / uma música).
- Player com visuais retrô, que se trocam pelo ícone de paleta no canto superior direito da tela do player (a escolha fica salva):
  - **Toca-discos de vinil**: o disco gira a 33⅓ rpm, acelera ao dar play e desacelera ao pausar; o braço desce sobre os sulcos e percorre o disco da borda até o centro conforme a música avança, e volta ao apoio ao pausar. Tocar no disco dá play/pausa.
  - **Toca-fitas cassete**: os carretéis giram e a fita passa de um lado para o outro conforme a música avança; contador digital e teclas anterior, play/pausa e próxima (o play fica afundado enquanto toca).
  - **Gravador de rolo**: dois carretéis grandes, mostradores analógicos de tempo decorrido e restante e as mesmas teclas.
  - **Clássico**: o visual simples anterior.
- Baixa músicas (uma a uma ou a pasta inteira) para ouvir sem internet; a aba "Baixadas" funciona sem conexão.
- Aba "Baixadas": toque no ícone de seleção (ou segure o dedo sobre uma música) para marcar várias, use "selecionar todas" e exclua de uma vez, com confirmação.
- Dois contadores de tamanho na aba "Baixadas": **Total baixado (histórico)**, que soma tudo o que já foi baixado e não diminui ao excluir, e **Na pasta agora**, que mostra só o que está guardado no momento.
- Android Auto: o app aparece como aplicativo de mídia e deixa navegar por Baixadas, OneDrive (com subpastas) e Neste celular. Veja a seção abaixo.
- Menu ⋮ na aba OneDrive: "Link da pasta do OneDrive" e "Sobre" (versão, data da última atualização, compatibilidade, desenvolvedor, contato e nota de proteção de dados).
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

## Letra da música

No player, o ícone de letra (ao lado do de tema) troca a imagem do tocador pelo painel de letra.

1. Sozinho: na primeira vez que uma música toca (com o app aberto), o app já busca e salva a letra. Ou toque em "Buscar letra". O app procura a letra sincronizada no LRCLIB (lrclib.net, gratuito, sem chave) pelo título, artista e duração, e salva no celular.
2. Nas próximas vezes a letra abre na hora, mesmo sem internet. A letra inteira fica na tela, com a linha atual em destaque; "Seguir a música" faz a lista acompanhar sozinha, e tocar numa linha pula para esse ponto.
3. Funciona melhor com arquivos nomeados "Título - Artista".
4. "Opções": desligue "Buscar a letra automaticamente" se preferir buscar só quando tocar no botão.

A transcrição por IA (Whisper) está preparada no código (`lyrics/`), mas desligada: a biblioteca sherpa-onnx não existe no Maven Central e precisa ser incluída de outro modo (`WhisperEngine.AVAILABLE`).

## Android Auto

1. Instale o app e abra uma vez. Na aba "Celular", conceda a permissão "Música e áudio" se quiser ver as músicas do aparelho no carro.
2. Como o app é instalado por APK (e não pela Play Store), o Android Auto só o mostra com uma opção de desenvolvedor ligada: abra o app Android Auto no celular, toque dez vezes em "Versão", abra o menu ⋮ > "Configurações do desenvolvedor" e ative "Fontes desconhecidas".
3. Conecte o celular ao carro. O "MP3 Pro Player" aparece na lista de aplicativos de mídia.

Limites: pastas escolhidas pelo seletor (SAF) não aparecem no carro; só Baixadas, OneDrive e as músicas indexadas pelo Android. As músicas do OneDrive precisam de internet no celular, a menos que já estejam baixadas.

## Estrutura

- `audio/`: equalizador, efeitos e persistência dos ajustes (`AudioFxProcessor.kt` é o motor de áudio).
- `data/`: leitura do OneDrive, downloads e armazenamento offline.
- `player/`: serviço de reprodução em segundo plano (Media3) e o controlador usado pela tela.
- `ui/`: telas em Jetpack Compose.
