# NBP Cobble Plus - Modpack & Server Suite

Mod oficial desenvolvido para o **Time NBP** e focado em ser o núcleo multifuncional para o modpack e servidor de **Cobblemon** (Minecraft 1.21.1 - Fabric & NeoForge).

---

## 🎯 Intuito e Propósito do Mod

O **NBP Cobble Plus** foi criado para atuar como o **sistema central do servidor NBP**, reunindo diversas ferramentas, utilitários, automações e integrações em um único lugar, sem a necessidade de instalar múltiplos mods pequenos ou plugins separados.

### 💡 Filosofia Central: **100% Configurável**
Uma das maiores diretrizes do mod é a **total configurabilidade**. Nenhuma mensagem, tempo de recarga ou recurso fica fixo no código. Tudo pode ser ativado, desativado ou alterado facilmente através do arquivo de configuração central:
📁 `config/nbp_cobble_plus.json`

---

## 🧩 Arquitetura Modular (`Feature Modules`)

O mod adota um sistema de **Módulos Independentes**. Cada funcionalidade do servidor é um módulo isolado gerenciado pelo `FeatureManager`, permitindo recarregar configurações e habilitar/desabilitar recursos em tempo real.

### Módulos Atuais:

1. **Boas-Vindas (`WelcomeFeature`)**
   - Envia mensagens personalizadas no chat quando um jogador entra no servidor.
   - Suporta mensagem especial e diferenciada para **novos jogadores** em seu primeiro login no servidor.

2. **Anunciador Automático (`AutoAnnouncerFeature`)**
   - Transmite anúncios e dicas automáticas no chat em intervalos de tempo pré-definidos (ex: a cada 5 minutos).
   - Suporta lista ilimitada de mensagens (regras, links do Discord, dicas do modpack).
   - Suporta exibição em ordem **sequencial** ou **aleatória**.

3. **Broadcasts de Eventos Cobblemon (`BroadcastFeature`)**
   - Anuncia capturas de Pokémon no chat.
   - **Destaque Especial para Shinies**: Anuncia capturas de Pokémon **Shiny** com formatação de cor diferenciada para todo o servidor.
   - Anuncia quando um jogador escolhe seu Pokémon inicial (*Starter*).

4. **Cura de Equipe Pokémon (`PartyHealFeature`)**
   - Adiciona comandos para os treinadores curarem sua equipe Pokémon.
   - Gerencia tempo de recarga (*cooldown*) individual por jogador para evitar abusos em batalhas ou torneios.

5. **Combo de Capturas (`CatchComboFeature`)**
   - Sistema nativo (sem depender de KubeJS ou mods externos) inspirado no *Catch Combo* de Pokémon Let's Go: capturar a mesma espécie em sequência aumenta o combo; capturar uma espécie diferente reinicia o combo em 1.
   - Cada faixa de combo concede: **IVs perfeitos garantidos**, **chance de Shiny aumentada**, **multiplicador de EXP** e **chance maior de spawns raros** perto do jogador (via reponderação dos *spawn buckets* do Cobblemon).
   - Progressão padrão:

     | Combo Mínimo | IVs Perfeitos Garantidos | Multiplicador de Rare Spawns | Chance Shiny (multiplicador) | Multiplicador de XP |
     | :---: | :---: | :---: | :---: | :---: |
     | 0 – 10 | 0 | 1x a 3x (ramp) | x2.0 (~1 em 4096) | x1.1 |
     | 11 – 20 | 2 | 4x | x8.0 (~1 em 1024) | x1.5 |
     | 21 – 30 | 3 | 5x | x16.0 (~1 em 512) | x2.0 |
     | 31+ | 4 | 6x | x24.0 (~1 em 341) | Aumenta +0.5 a cada 10 combos |

   - Toda a progressão acima (`catchCombo.tiers`) é configurável — dá para adicionar, remover ou editar faixas livremente.
   - Recorde de combo (`bestCount`) é salvo por jogador em `config/nbp_cobble_plus_catchcombo.json`.
   - O status do combo aparece como **texto fixo no canto inferior direito da tela** (HUD), não no chat. É sincronizado do servidor para o cliente via um pacote próprio (`catch_combo_sync`).
   - **Compatível com o Cobblenav/Pokénav**: o bônus de spawns raros é aplicado como uma `SpawningInfluence` anexada ao `Spawner` do próprio jogador (`player.spawner.influences`), o mesmo mecanismo que o Cobblenav lê ao calcular a chance de spawn exibida no Pokénav. Ou seja, a chance mostrada no Pokénav aumenta de verdade conforme o combo cresce, em vez de ficar travada no valor base.

---

## 📜 Arquivo de Configuração (`config/nbp_cobble_plus.json`)

O arquivo é gerado automaticamente na pasta `config/` na primeira execução do servidor e pode ser recarregado sem reiniciar a máquina via o comando `/nbp reload`.

```json
{
  "welcome": {
    "enabled": true,
    "enableFirstJoinMessage": true,
    "chatMessage": "§a[NBP] Bem-vindo ao servidor NBP Cobble Plus, {player}!",
    "firstJoinChatMessage": "§6[NBP] Seja muito bem-vindo pela primeira vez ao servidor, {player}!"
  },
  "announcer": {
    "enabled": true,
    "intervalSeconds": 300,
    "announceInRandomOrder": false,
    "messages": [
      "§b[Dica NBP] Use §e/nbp help §bpara ver os comandos e ajuda do modpack!",
      "§b[Dica NBP] Participe da nossa comunidade e fique por dentro dos torneios e eventos!",
      "§b[Dica NBP] Respeite os outros treinadores e aproveite sua jornada Cobblemon!"
    ]
  },
  "broadcast": {
    "enableCaptureBroadcast": true,
    "captureMessage": "§f[NBP] O jogador §e{player} §fcapturou um §a{pokemon}§f! (Level {level})",
    "enableShinyCaptureBroadcast": true,
    "shinyCaptureMessage": "§6§l[NBP SHINY] §e{player} §fcapturou um Pokémon SHINY! §d★ {pokemon} ★ §f(Level {level})!",
    "enableStarterBroadcast": true,
    "starterMessage": "§a[NBP] O jogador §e{player} §fescolheu seu Pokémon inicial: §b{pokemon}§f!"
  },
  "partyHeal": {
    "enabled": true,
    "cooldownSeconds": 300,
    "healMessage": "§a[NBP] Sua equipe Pokémon foi totalmente curada!",
    "cooldownMessage": "§c[NBP] Aguarde {seconds} segundos para usar o cura-party novamente."
  },
  "catchCombo": {
    "enabled": true,
    "comboBreaksOnSpeciesChange": true,
    "enablePerfectIvBonus": true,
    "enableShinyBonus": true,
    "enableExpBonus": true,
    "enableRareSpawnBonus": true,
    "enableRecordMessage": true,
    "rareSpawnRampCombo": 10,
    "xpMultiplierStepSize": 10,
    "xpMultiplierIncrementPerStep": 0.5,
    "rareSpawnBucketNames": ["rare", "ultra-rare"],
    "comboMessage": "§b[Combo] §f{pokemon} §ax{count}",
    "hudBonusLineFormat": "§7IVs {ivs} §7| Shiny x{shiny} §7| Spawn x{rare} §7| EXP x{xp}",
    "perfectIvSuffix": " §d| +{amount} IV(s) perfeito(s)",
    "newRecordMessage": "§6[Combo] §fNovo recorde: §e{pokemon} §fx{count}!",
    "noComboMessage": "§7[Combo] Nenhum combo ativo no momento.",
    "resetMessage": "§c[Combo] Seu combo atual foi resetado.",
    "tiers": [
      { "minCombo": 0, "guaranteedPerfectIvs": 0, "rareSpawnMultiplier": 3.0, "shinyChanceMultiplier": 2.0, "xpMultiplier": 1.1 },
      { "minCombo": 11, "guaranteedPerfectIvs": 2, "rareSpawnMultiplier": 4.0, "shinyChanceMultiplier": 8.0, "xpMultiplier": 1.5 },
      { "minCombo": 21, "guaranteedPerfectIvs": 3, "rareSpawnMultiplier": 5.0, "shinyChanceMultiplier": 16.0, "xpMultiplier": 2.0 },
      { "minCombo": 31, "guaranteedPerfectIvs": 4, "rareSpawnMultiplier": 6.0, "shinyChanceMultiplier": 24.0, "xpMultiplier": 2.5 }
    ]
  }
}
```

---

## 💻 Comandos no Jogo

| Comando | Permissão | Descrição |
| :--- | :--- | :--- |
| `/nbp` | Todos | Exibe o status e versão do mod. |
| `/nbp help` | Todos | Exibe o menu de ajuda e comandos. |
| `/nbp modules` | Todos | Lista os módulos de recursos e seu status (`[ATIVO]` ou `[DESATIVADO]`). |
| `/nbp heal` ou `/pokeheal` | Todos | Cura a equipe de Pokémon do jogador (respeita o cooldown da config). |
| `/nbp combo` | Todos | Mostra o combo de capturas atual e os bônus ativos. |
| `/nbp combo reset` | Todos | Reseta o combo de capturas atual (mantém o recorde). |
| `/nbp reload` | Admin (Nível 2) | Recarrega as configurações do JSON e atualiza todos os módulos. |
| `/nbp announce` | Admin (Nível 2) | Força o envio imediato do próximo anúncio do anunciador automático. |

---

## 🛠️ Tecnologias e Plataformas

- **Linguagem**: Kotlin 2.1
- **Minecraft**: 1.21.1
- **Cobblemon**: 1.7.0+
- **Plataformas**: Fabric Loader & NeoForge Loader (Multi-loader via Architectury)
