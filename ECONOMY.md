# Economia do NBP Cobble Plus

## Objetivo

O CobbleDollars é a moeda central do modpack. Capturas, batalhas, eventos e sistemas de outros mods devem movimentar a mesma moeda, mas toda nova fonte precisa respeitar os limites descritos aqui. O objetivo é manter consumíveis básicos acessíveis sem permitir que itens fortes ou atalhos de progressão se tornem triviais.

## Fontes de renda

O módulo NBP paga por:

- Capturar um Pokémon.
- Vencer uma batalha contra Pokémon selvagens.

Não existe pagamento por PvP. Uma captura feita durante uma batalha recebe apenas a recompensa de captura; a vitória não é paga novamente.

As recompensas nativas do CobbleDollars por NPC, Pokémon selvagem e Quick Battle são desativadas quando `disableNativeCobbleDollarsRewards` está ativo. Isso evita pagamentos duplicados e mantém o NBP como fonte autoritativa.

## Cálculo padrão

Captura:

`8 + (nível × 0,35)`

Derrota:

`5 + (nível × 0,25)`

O resultado pode receber os seguintes multiplicadores:

- Shiny: `3×`.
- Lendário: `4×`.
- Limite por Pokémon: `250 CobbleDollars`.

A lista do spawner lendário é reconhecida automaticamente. Espécies especiais de chefes ou rituais podem ser incluídas em `legendaryBonusSpecies`.

## Proteções contra farm e inflação

Cada ação e espécie possui um contador separado por jogador. Por padrão:

- As primeiras cinco recompensas da mesma ação e espécie pagam 100%.
- A janela dura 30 minutos.
- Recompensas adicionais durante a janela pagam 20%.
- Ao chegar a 3.000 CobbleDollars no mesmo dia, novos ganhos passam a pagar 25%.
- O teto diário absoluto é 5.000 CobbleDollars por jogador.
- O dia econômico vira à meia-noite UTC.

Os dados são persistidos por UUID no save do mundo. Reiniciar o servidor ou reconectar não remove os limites.

## Loja

A loja padrão fica em `pack_defaults/config/cobbledollars/default_shop.json` e possui 76 ofertas compatíveis com os mods essenciais, distribuídas entre:

- Poké Balls.
- Medicine.
- Training.
- Evolution.
- Trainer Utilities.

Ofertas de Waystones e Sophisticated Backpacks ficam em `optional_mod_offers.json.disabled`. Elas só devem ser incorporadas quando esses mods estiverem realmente instalados: o CobbleDollars rejeita a loja inteira se qualquer oferta usar um ID inexistente.

Faixas de referência:

| Faixa | Preço aproximado | Exemplos |
| :--- | ---: | :--- |
| Consumo básico | 60–500 | Potion, Poké Ball, Great Ball |
| Consumo especializado | 650–2.500 | Balls especiais, Revive, Luxury Ball |
| Progressão intermediária | 3.000–18.000 | Pedras, mints, máquinas básicas |
| Conveniência forte | 25.000–90.000 | Pasture, Ability Patch, mochilas avançadas |
| Fim de jogo | 120.000–320.000 | Master Ball e upgrades de alto nível |

Itens especialmente fortes podem usar `stock` limitado. Master Ball, Beast Ball, Exp Share e alguns upgrades estão limitados por loja.

## Itens que não devem ser vendidos normalmente

Os seguintes grupos ficam reservados para exploração, quests, crafting, chefes ou eventos:

- Itens criativos ou administrativos.
- Itens de invocação e rituais do NBP.
- Mega Stones, braceletes, Z-Rings e itens equivalentes de progressão especial.
- Componentes que liberam etapas decisivas de automação.
- Prêmios exclusivos de eventos.

Adicionar esses itens à loja exige análise específica. Preço alto sozinho nem sempre protege a progressão, especialmente quando novas fontes de renda forem introduzidas.

## Banco e venda de recursos

O banco padrão fica vazio em `pack_defaults/config/cobbledollars/default_bank.json`. A troca original de esmeraldas foi removida porque villagers e farms poderiam gerar moeda ilimitada.

Novos itens vendáveis devem atender a pelo menos uma destas condições:

- Possuir obtenção limitada ou controlada.
- Ter limite diário de venda.
- Ser consumido por outro sistema importante do pack.
- Pagar muito menos que seu custo de compra e produção.

Recursos renováveis, automatizáveis ou duplicáveis não devem ser convertidos diretamente em CobbleDollars sem limite.

## Eventos e outros mods

### Cobblemon Raid Dens

O Raid Dens possui integração oficial com CobbleDollars e credita a moeda quando o jogador reivindica a recompensa de uma vitória. O NBP substitui os valores nativos, que chegariam a 100.000, pela curva configurável `raidDensRewardsByTier`:

| Tier | Recompensa padrão |
| ---: | ---: |
| 1 | 200 |
| 2 | 400 |
| 3 | 750 |
| 4 | 1.250 |
| 5 | 4.500 |
| 6 | 10.000 |
| 7 | 20.000 |

Uma batalha de Raid Den não recebe a recompensa comum de vitória selvagem do NBP. O prêmio oficial da raid é o único pagamento, evitando duplicação. Como o Raid Dens controla tentativas, clears e distribuição dos prêmios, essa renda premium não consome o limite diário comum. Os tiers 5–7 pagam acima do teto diário porque exigem múltiplos jogadores, Pokémon de nível 100 bem treinados e possuem risco elevado de falha.

Toda nova fonte deve definir:

1. Ganho médio esperado por jogador.
2. Frequência e duração.
3. Possibilidade de automação ou uso de contas alternativas.
4. Relação com o teto diário normal.
5. Itens ou serviços que removerão essa moeda da economia.

Eventos comuns devem trabalhar dentro do teto diário ou conceder valores moderados. Premiações que ignoram o teto devem ser raras, registradas e acompanhadas de novos sumidouros de moeda.

Evite recompensar apenas participação passiva. Prefira objetivos verificáveis, colocação, missões limitadas ou moeda liberada em etapas.

## Configuração

As regras ficam na seção `economy` de `config/nbp_cobble_plus.json`. Os campos principais são:

| Campo | Função |
| :--- | :--- |
| `captureBase` / `capturePerLevel` | Recompensa de captura. |
| `defeatBase` / `defeatPerLevel` | Recompensa de vitória selvagem. |
| `shinyMultiplier` | Bônus de shiny. |
| `legendaryMultiplier` | Bônus de lendário. |
| `maximumRewardPerPokemon` | Proteção contra uma recompensa individual excessiva. |
| `repeatWindowMinutes` | Duração da janela anti-farm. |
| `fullRewardsPerSpeciesAndAction` | Quantidade paga integralmente por janela. |
| `repeatedRewardMultiplier` | Fração paga depois do limite de repetição. |
| `dailySoftCapStart` | Ponto onde a renda começa a desacelerar. |
| `softCapMultiplier` | Fração paga depois do soft cap. |
| `dailyEarningCap` | Teto diário absoluto. |
| `applyCobbleDollarsGlobalMultiplier` | Permite ou bloqueia o multiplicador global do CobbleDollars. |
| `disableNativeCobbleDollarsRewards` | Desliga as fontes nativas duplicadas. |
| `configureRaidDensRewards` | Faz o NBP controlar os prêmios monetários do Raid Dens. |
| `raidDensRewardsByTier` | Sete valores, do tier 1 ao tier 7. |

## Comandos

- `/nbp economy`: auditoria administrativa que compara o saldo oficial do CobbleDollars com o valor creditado pelo NBP no dia; requer permissão 2.
- `/nbp economy reset <jogador>`: remove os dados econômicos persistidos do jogador; requer permissão 2.

## Procedimento de balanceamento

Antes de alterar preços ou recompensas, registre durante testes:

- Renda obtida em 30 e 60 minutos por jogador iniciante, intermediário e avançado.
- Quantidade média de Poké Balls consumidas.
- Tempo necessário para comprar itens intermediários e de fim de jogo.
- Quantidade total de moeda existente entre jogadores.
- Maiores fontes e maiores sumidouros no período.

O preço de um item forte deve ser baseado em tempo de progressão e impacto, não apenas em raridade. Mudanças grandes devem ser graduais para não beneficiar excessivamente quem acumulou moeda antes do reajuste.
