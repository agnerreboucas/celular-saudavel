# Celular Saudável — versão de teste (Android)

Kotlin + Jetpack Compose. Princípio: **PROTEGER → VERIFICAR → LIBERAR**.

## Instalar no celular

1. Abra a aba **Releases** deste repositório no celular (logado no GitHub).
2. Baixe `CelularSaudavel.apk` da versão mais recente.
3. Toque no arquivo. Se o Android pedir, permita "instalar apps desta fonte" para o navegador.
4. Abra o app e toque em **Analisar meu celular**.

Cada push na branch `main` gera um APK novo automaticamente (GitHub Actions).
Como a chave de assinatura é fixa, versões novas instalam **por cima** da anterior.

## O que funciona com dados reais

- Armazenamento total/usado/livre e divisão por fotos, vídeos, áudios e apps
- Índice de saúde calculado (ocupação, duplicadas, vídeos grandes, apps sem uso)
- Duplicadas exatas (tamanho + MD5 do conteúdo), mantendo sempre ao menos 1 cópia
- Vídeos acima de 100 MB, enviados para a **lixeira** do sistema (recuperável ~30 dias)
- Apps instalados: tamanho, último uso (com "Acesso ao uso"), desinstalação pela janela do Android
- Histórico local, tela de permissões, privacidade

## Ainda não implementado

- Backup no Google Drive (precisa de projeto no Google Cloud + OAuth com o SHA-1 abaixo)
- Fotos semelhantes, WhatsApp, notificações, check-up periódico (v1.5)

## Chave de teste

`keystore/dev.jks` (senha `celularsaudavel`, alias `dev`) — **só para testes**.
SHA-1 (para cadastrar o OAuth do Google Drive): `33:BF:CA:3D:5A:78:F1:1B:3A:1D:72:D1:E3:A2:DE:3A:13:0E:87:96`
Para a Play Store, gere uma chave nova e guarde-a fora do repositório.

## Play Store — atenção

`QUERY_ALL_PACKAGES` e `READ_MEDIA_IMAGES/VIDEO` são permissões restritas na Play Store e exigem
declaração justificando o uso. Para instalação direta (APK) não há restrição.

## Estrutura

```
app/src/main/java/br/com/celularsaudavel/
├── MainActivity.kt          navegação e permissões
├── model/Models.kt          modelos, índice de saúde, formatação
├── data/MediaRepository.kt  armazenamento, MediaStore, duplicadas, remoção
├── data/AppsRepository.kt   apps, tamanho, último uso
├── data/HistoryStore.kt     histórico local
└── ui/                      ViewModel, componentes e telas
```

Versões: AGP 8.9.1 · Kotlin 2.1.20 · Gradle 8.14.3 · compileSdk 35 · minSdk 26 (Android 8).
