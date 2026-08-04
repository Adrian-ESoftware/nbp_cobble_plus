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
   - Cada faixa de combo concede: **IVs perfeitos garantidos** (aplicados no momento em que o Pokémon selvagem nasce perto do jogador, não só no que for capturado), **chance de Shiny aumentada**, **multiplicador de EXP**, **chance maior de spawns raros** perto do jogador (bucket inteiro) e **chance maior da própria espécie do combo nascer** (quanto mais Magikarp capturado em sequência, maior o peso de spawn do Magikarp especificamente) — tudo via `SpawningInfluence` anexada ao `Spawner` do próprio jogador, o mesmo dado que o Cobblenav/Pokénav lê.
   - Progressão padrão:

     | Combo Mínimo | IVs Perfeitos Garantidos | Multiplicador de Rare Spawns | Chance Shiny (multiplicador) | Multiplicador de XP |
     | :---: | :---: | :---: | :---: | :---: |
     | 0 – 10 | 0 | 1x a 3x (ramp) | x2.0 (~1 em 4096) | x1.1 |
     | 11 – 20 | 2 | 4x | x8.0 (~1 em 1024) | x1.5 |
     | 21 – 30 | 3 | 5x | x16.0 (~1 em 512) | x2.0 |
     | 31+ | 4 | 6x | x24.0 (~1 em 341) | Aumenta +0.5 a cada 10 combos |

   - Toda a progressão acima (`catchCombo.tiers`) é configurável — dá para adicionar, remover ou editar faixas livremente.
   - O progresso do combo (espécie, contagem, recorde, HUD visível/escondido) é salvo por jogador como **dado do mundo** (`SavedData`, igual a mapas e scoreboard do próprio Minecraft) em vez de um arquivo em `config/` — cada save/servidor tem seu próprio progresso.
   - O HUD e o `/nbp combo` mostram a **chance real (%) de spawn da espécie do combo** (não do bucket) já com o bônus aplicado, e a **chance real (%) dela vir shiny** — calculado refazendo a mesma resolução de spawn que o Cobblemon usa de verdade (zona de spawn, posições válidas perto do jogador, pesos com a `SpawningInfluence` já aplicada).
   - O status do combo aparece como **texto fixo no canto inferior direito da tela** (HUD), não no chat. É sincronizado do servidor para o cliente via um pacote próprio (`catch_combo_sync`).
   - **Compatível com o Cobblenav/Pokénav**: o bônus de spawns raros é aplicado como uma `SpawningInfluence` anexada ao `Spawner` do próprio jogador (`player.spawner.influences`), o mesmo mecanismo que o Cobblenav lê ao calcular a chance de spawn exibida no Pokénav. Ou seja, a chance mostrada no Pokénav aumenta de verdade conforme o combo cresce, em vez de ficar travada no valor base.

6. **Bloqueio de Mobs Vanilla (`VanillaMobSpawnBlockerFeature`)**
   - Impede a entrada no mundo de todos os mobs do namespace `minecraft`, independentemente da origem do spawn (natural, spawner, estrutura, comando, spawn egg ou invocação especial).
   - Por padrão, somente `minecraft:villager` e `minecraft:wandering_trader` são permitidos.
   - Jogadores, Pokémon, entidades de máquinas e entidades não-mob (projéteis, itens, veículos etc.) não são afetados.
   - A lista de exceções pode ser alterada em `vanillaMobSpawnBlocker.allowedEntityTypes`.
   - Origens especiais podem substituir o mob bloqueado por Pokémon temáticos: golem de ferro por Golurk, blocos infestados por Pokémon inseto e spawners de aranha, zumbi, esqueleto, blaze e magma cube por listas configuráveis de Pokémon semelhantes.
   - `pokemonReplacements` aceita propriedades completas do Cobblemon, permitindo definir espécie, nível, forma, shiny e outras características.
   - O nível informado em cada entrada funciona como nível-base. `replacementLevelVariance` sorteia uma variação para cima ou para baixo (por padrão ±5), portanto os Pokémon não surgem sempre no mesmo nível.
   - A construção do Wither invoca Necrozma no lugar dele. O Ender Dragon é substituído por Rayquaza; somente esse Rayquaza especial fica contido na ilha central. Derrotá-lo, capturá-lo ou removê-lo conclui oficialmente a luta, abre o portal e concede a progressão do dragão aos jogadores próximos.
   - `maxNearbyReplacements` limita os Pokémon equivalentes próximos e impede acúmulo infinito causado por spawners. O padrão é 12 por grupo em um raio de 16 blocos, mantendo spawners povoados sem sobrecarregar os ticks de IA.
   - Trial Spawners contabilizam a tentativa e respeitam seu intervalo normal mesmo quando o teto local já foi alcançado, evitando tentativas de substituição a cada tick.
   - Nas Trial Chambers, o Pokémon herda o UUID acompanhado pelo Trial Spawner. A recompensa só é liberada depois que os Pokémon forem removidos — por derrota, dano direto, captura ou despawn.

7. **Spawn Global de Lendários (`LegendarySpawnerFeature`)**
   - Substitui o antigo `legendary.js` por um sorteio nativo na ordem dimensão → espécie → posição segura. A área ocupada por oceanos não altera mais a chance das espécies aquáticas.
   - Overworld, Nether e End têm pesos independentes. Por padrão os três recebem o mesmo peso quando possuem jogadores, dando presença real aos pools menores do Nether e do End.
   - A busca usa somente chunks já carregados perto de jogadores, evitando geração de terreno e picos de TPS.
   - No End, um lendário terrestre só nasce sobre piso sólido com espaço livre, nunca no void. No Nether, a busca vertical encontra plataformas seguras próximas do jogador.
   - O histórico é salvo por mundo. Com `reduceRepeatChance` ativo, uma espécie que já nasceu recebe chance 10 vezes menor nos próximos sorteios, priorizando diversidade.
   - O anfitrião também é balanceado por histórico: jogadores com menos lendários próximos recebem prioridade. Após um spawn, o peso daquele jogador cai até os demais alcançarem sua contagem.
   - Afinidades de bioma e horário são preservadas; `allowBiomeFallback` evita que biomas raros impeçam completamente um spawn, sem permitir água para terrestres ou chão seco para aquáticos.

8. **Meltan na Fornalha (`MeltanFurnaceFeature`)**
   - Cada lingote de ferro retirado manualmente de uma fornalha ou alto-forno tem 0,01% de chance de gerar um Meltan acima da fornalha.
   - Retirar uma pilha realiza um sorteio independente por lingote; automação sem jogador não aciona o evento.
   - O Meltan recebe quatro IVs perfeitos aleatórios por padrão.

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
    "enableCaptureBroadcast": false,
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
  "meltanFurnace": {
    "enabled": true,
    "chancePerIronIngot": 0.0001,
    "perfectIvCount": 4,
    "horizontalSearchRadius": 6,
    "verticalSearchRadius": 4,
    "validFurnaces": ["minecraft:furnace", "minecraft:blast_furnace"]
  },
  "vanillaMobSpawnBlocker": {
    "enabled": true,
    "allowedEntityTypes": [
      "minecraft:villager",
      "minecraft:wandering_trader"
    ],
    "enablePokemonReplacements": true,
    "replacementRadius": 16.0,
    "maxNearbyReplacements": 12,
    "replacementLevelVariance": 5,
    "pokemonReplacements": {
      "minecraft:iron_golem": ["species=golurk level=35"],
      "minecraft:silverfish": ["species=durant level=10", "species=sizzlipede level=10"],
      "minecraft:endermite": ["species=dottler level=15", "species=nincada level=12", "species=venipede level=12", "species=orthworm level=18"],
      "minecraft:spider": ["species=spidops level=15", "species=ariados level=15", "species=galvantula level=15"],
      "minecraft:cave_spider": ["species=tarountula level=10", "species=joltik level=10", "species=dewpider level=10"]
    }
  },
  "legendarySpawner": {
    "enabled": true,
    "intervalMinutes": 30,
    "spawnChance": 0.25,
    "minimumDistanceFromPlayer": 48,
    "maximumDistanceFromPlayer": 128,
    "locationAttempts": 48,
    "allowBiomeFallback": true,
    "minimumLevel": 60,
    "maximumLevel": 75,
    "perfectIvCount": 4,
    "reduceRepeatChance": true,
    "repeatChanceDivisor": 10.0,
    "balanceSpawnsBetweenPlayers": true,
    "playerRepeatChanceDivisor": 4.0,
    "dimensionWeights": {
      "minecraft:overworld": 1.0,
      "minecraft:the_nether": 1.0,
      "minecraft:the_end": 1.0
    },
    "announceSpawn": true,
    "includeCoordinatesInAnnouncement": true,
    "spawnMessage": "§6[Lendário] §e{pokemon} §fsurgiu em §d{dimension} §fpróximo de §a{player}§f!",
    "legendaryPool": [
      {
        "species": "lugia",
        "dimensions": ["minecraft:overworld"],
        "biomes": ["#cobblemon:is_ocean"],
        "times": ["night"],
        "aquatic": true,
        "weight": 1.0
      }
    ]
  },
  "catchCombo": {
    "enabled": true,
    "comboBreaksOnSpeciesChange": true,
    "enablePerfectIvBonus": true,
    "enableShinyBonus": true,
    "enableExpBonus": true,
    "enableRareSpawnBonus": true,
    "enableSpeciesSpawnBonus": true,
    "enableRecordMessage": true,
    "rareSpawnRampCombo": 10,
    "xpMultiplierStepSize": 10,
    "xpMultiplierIncrementPerStep": 0.5,
    "rareSpawnBucketNames": ["rare", "ultra-rare"],
    "comboMessage": "§b[Combo] §f{pokemon} §ax{count}",
    "hudBonusLineFormat": "§7IVs {ivs} §7| Shiny x{shiny} §7| Spawn x{rare} §7| EXP x{xp}",
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

## 🎁 Loot específico de Pokémon

O arquivo separado `config/nbp_cobble_plus/pokemon_loot.json` é criado automaticamente. As regras são aplicadas depois que o Cobblemon escolhe seus drops originais, permitindo adicionar e remover somente itens específicos:

```json
{
  "enabled": true,
  "species": {
    "pikachu": {
      "enabled": true,
      "remove": ["minecraft:redstone"],
      "add": [
        {
          "item": "minecraft:diamond",
          "chance": 0.05,
          "minQuantity": 1,
          "maxQuantity": 2
        }
      ]
    }
  }
}
```

`chance` usa valores de `0.0` a `1.0`. IDs de espécie podem ser escritos como `pikachu` ou `cobblemon:pikachu`. Use `/nbp reload` após editar o arquivo.

### Limite de captura

O módulo `captureCap` impede que uma Poké Ball capture Pokémon acima do limite efetivo do jogador. O progresso é persistido por UUID no save do mundo. O limite pode subir por avanços do Minecraft, pelo item `nbp_cobble_plus:capture_permit` ou por comando administrativo.

```json
"captureCap": {
  "enabled": true,
  "defaultCap": 13,
  "maximumCap": 100,
  "itemUpgrades": [
    {
      "item": "nbp_cobble_plus:capture_permit",
      "increase": 5,
      "cap": 0,
      "consume": true
    },
    {
      "item": "outro_mod:item_customizado",
      "increase": 0,
      "cap": 50,
      "consume": true
    }
  ],
  "advancements": [
    { "advancement": "minecraft:story/mine_diamond", "cap": 25 },
    { "advancement": "minecraft:nether/obtain_blaze_rod", "cap": 35 },
    { "advancement": "minecraft:end/kill_dragon", "cap": 50 },
    { "advancement": "minecraft:nether/summon_wither", "cap": 70 },
    { "advancement": "minecraft:end/elytra", "cap": 100 }
  ]
}
```

Cada conquista define um patamar mínimo, sem reduzir aumentos já recebidos. Em `itemUpgrades`, `increase` soma níveis ao limite atual e `cap` libera um patamar mínimo específico; IDs de itens de outros mods são aceitos. A Licença de Captura não possui receita padrão para poder ser distribuída por quests, loot ou `/give`.

### Apiários Pokémon

Colmeias e ninhos carregados produzem mel quando existe um Combee ou Vespiquen ativo por perto, incluindo Pokémon mantidos para fora por um Pasture Block. É necessário ser dia e haver flores próximas. Combee produz um nível de mel em 4–6 minutos; Vespiquen em 2–3 minutos. Os valores, raios e condições ficam na seção `pokemonApiary` da configuração.

O sistema altera o `HONEY_LEVEL` vanilla, portanto garrafas, tesouras, dispensers e fogueiras continuam funcionando normalmente. Quando mantidos em um Pasture, Combee produz Honeycomb ocasionalmente (5–10 minutos) e Vespiquen produz Honey Bottle (3–6 minutos), sem precisar morrer. Também existe uma receita sem forma de Honeycomb + Glass Bottle para Honey Bottle.

Na primeira criação do arquivo, o mod inclui Nether Star para alguns lendários: Arceus (100%), Eternatus (75%), Necrozma (75%), Deoxys (50%) e Giratina (50%). Essas regras podem ser alteradas ou removidas normalmente.

## 💰 Economia com CobbleDollars

A arquitetura, fórmulas, proteções contra inflação, preços da loja e orientações para futuras integrações estão documentadas separadamente em [ECONOMY.md](ECONOMY.md).

## 💻 Comandos no Jogo

| Comando | Permissão | Descrição |
| :--- | :--- | :--- |
| `/nbp` | Todos | Exibe o status e versão do mod. |
| `/nbp help` | Todos | Exibe o menu de ajuda e comandos. |
| `/nbp lang` | Todos | Mostra o idioma pessoal atual e os idiomas disponíveis. |
| `/nbp lang en_us` ou `/nbp lang pt_br` | Todos | Altera e salva o idioma das mensagens apenas para o jogador. |
| `/nbp modules` | Todos | Lista os módulos de recursos e seu status (`[ATIVO]` ou `[DESATIVADO]`). |
| `/nbp heal` ou `/pokeheal` | Todos | Cura a equipe de Pokémon do jogador (respeita o cooldown da config). |
| `/nbp combo` | Todos | Mostra o combo de capturas atual e os bônus ativos. |
| `/nbp combo reset` | Todos | Reseta o combo de capturas atual (mantém o recorde). |
| `/nbp combo hud` | Todos | Mostra ou esconde o HUD do combo no canto da tela. |
| `/nbp capturecap` | Todos | Mostra o limite efetivo de captura do jogador. |
| `/nbp capturecap add <jogador> <níveis>` | Admin (Nível 2) | Aumenta o limite persistente do jogador. |
| `/nbp capturecap set <jogador> <nível>` | Admin (Nível 2) | Define o limite persistente do jogador. |
| `/nbp capturecap reset <jogador>` | Admin (Nível 2) | Remove o ajuste persistente e volta ao limite da configuração/conquistas. |
| `/nbp economy` | Admin (Nível 2) | Compara o saldo CobbleDollars com quanto o NBP creditou no limite diário. |
| `/nbp economy reset <jogador>` | Admin (Nível 2) | Reseta os dados econômicos persistidos do jogador. |
| `/nbp legendary test` | Admin (Nível 2) | Força o sorteio e o spawn seguro de um lendário próximo ao executor. |
| `/nbp legendary test <espécie>` | Admin (Nível 2) | Testa uma espécie específica válida para a dimensão atual, ignorando horário. |
| `/nbp legendary test-natural` | Admin (Nível 2) | Executa o ciclo natural completo, sorteando dimensão, jogador, espécie e posição. |
| `/nbp legendary chance` | Admin (Nível 2) | Mostra a chance efetiva do executor ser o próximo anfitrião. |
| `/nbp legendary available` | Admin (Nível 2) | Lista espécies compatíveis com a dimensão, horário e bioma atuais. |
| `/nbp legendary history` | Admin (Nível 2) | Exibe a contagem e a chance efetiva (%) de cada jogador online ser o próximo anfitrião. |
| `/nbp legendary reset-history` | Admin (Nível 2) | Reseta a diversidade e o balanceamento persistentes do mundo. |
| `/nbp reload` | Admin (Nível 2) | Recarrega as configurações do JSON e atualiza todos os módulos. |
| `/nbp announce` | Admin (Nível 2) | Força o envio imediato do próximo anúncio do anunciador automático. |

---

## 🛠️ Tecnologias e Plataformas

- **Linguagem**: Kotlin 2.1
- **Minecraft**: 1.21.1
- **Cobblemon**: 1.7.0+
- **Plataformas**: Fabric Loader & NeoForge Loader (Multi-loader via Architectury)
