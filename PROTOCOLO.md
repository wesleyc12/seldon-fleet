# PROTOCOLO.md: Seldon Fleet

> Protocolo de decisões do desafio Seldon Fleet (REQ-10).
> Este documento foi escrito e versionado **antes da implementação do código**, como pré-registro do critério de classificação (seções 3.3 e 4.4 da especificação).
> Objetivo: quem ler apenas este arquivo deve conseguir prever o status que o app atribui a qualquer registro do `frota.json`.

**Autor:** Wesley de Castro Silva
**Data do pré-registro:** 25/10/2026

---

## 1. Regra de classificação adotada

### Decisão
Escolhi a **regra de referência do PDF** (seção 4.2 da especificação), sem alterar nenhum limiar.

### Raciocínio
Escolhi a regra de referência do PDF porque, sem dados reais sobre o comportamento daquela frota, arbitrar outros limiares não seria tão objetivo e eficiente. Além disso, alterar a regra depois de abrir o arquivo violaria o princípio do pré-registro.

### Grandezas calculadas
As duas derivadas são calculadas **depois** da normalização de unidades (seção 2 deste protocolo):

| Derivada | Fórmula | Unidade |
|---|---|---|
| Dreno absoluto | `energiaMah ÷ (screenOnMinutes ÷ 60)` | mAh/h |
| Dreno relativo | `(drenoAbsoluto ÷ batteryCapacityMah) × 100` | %/h |

A classificação usa o **dreno relativo**, e não o absoluto, porque ele permite comparar aparelhos com baterias de capacidades diferentes.

### Estados e limiares
Os estados são avaliados **nesta ordem**. Vale o **primeiro** cuja condição for verdadeira. Dentro de cada estado, **qualquer uma** das condições basta.

| Ordem | Estado | Condição (qualquer uma basta) |
|---|---|---|
| 1 | `INDETERMINADO` | `batteryCapacityMah` ≤ 0 ou ausente · `screenOnMinutes` ≤ 0 · `energyUnit` = mWh com `nominalVoltage` ausente ou ≤ 0 |
| 2 | `CRITICO` | dreno relativo ≥ 12 %/h · `crashCount7d` ≥ 3 · `jankFramesPct` ≥ 15 |
| 3 | `ATENCAO` | dreno relativo ≥ 7 %/h · `crashCount7d` ≥ 1 · `jankFramesPct` ≥ 8 |
| 4 | `OK` | nenhuma das condições anteriores |

- Todas as comparações usam **"maior ou igual" (≥)**. Um dreno de exatamente 12,0 %/h já é `CRITICO`; 11,99 %/h não é.
- **Precedência:** se um aparelho tem ao mesmo tempo um sinal de `CRITICO` e um de `ATENCAO`, ele é `CRITICO`, porque o `CRITICO` é avaliado antes.
- Os nomes `OK`, `ATENCAO`, `CRITICO` e `INDETERMINADO` pertencem ao domínio. Os textos exibidos na tela (por exemplo, "SEM DADOS" para `INDETERMINADO`) pertencem à interface.

### Exemplos de aplicação (Anexo A da especificação)
- **DEV-0137** (energia em mAh): 980 ÷ (214 ÷ 60) = 274,8 mAh/h → 274,8 ÷ 4410 × 100 = **6,2 %/h**, abaixo de 7. Mas `crashCount7d` = 2, que é ≥ 1 ⇒ **`ATENCAO`**.
- **DEV-0204** (energia em mWh): 3773 ÷ 3,85 = 980 mAh → 980 ÷ (186 ÷ 60) = 316,1 mAh/h → **6,3 %/h**, nenhum sinal disparado ⇒ **`OK`**.

---

## 2. Tratamento do Defeito 1: unidade de energia inconsistente

### O problema
Parte dos registros informa `energyConsumed` em mAh (carga) e parte em mWh (energia). Comparar os dois sem converter gera um erro de cerca de 3,85× nos registros em mWh. No exemplo do DEV-0204, o dreno sairia 24,3 %/h (`CRITICO`) em vez de 6,3 %/h (`OK`): o defeito inverte o veredito.

### Decisão
- Todo valor em mWh é convertido para mAh usando a tensão nominal do próprio registro: **`mAh = mWh ÷ nominalVoltage`**.
- A conversão acontece **na camada de dados**, no momento em que o registro é lido do arquivo, **antes de qualquer comparação, soma ou classificação** (REQ-02).
- A camada de domínio recebe a energia **sempre em mAh** e não conhece a unidade original.
- No arquivo, **10 registros** estão em mWh: DEV-0204, DEV-0522, DEV-0630, DEV-0854, DEV-1142, DEV-1214, DEV-1422, DEV-1546, DEV-1582 e DEV-1924. Todos têm `nominalVoltage` entre 3,80 e 3,88 V.

---

## 3. Tratamento do Defeito 2: registros incompletos

### O problema
O dreno tem duas divisões: pelo tempo de tela e pela capacidade da bateria. Se qualquer uma delas for zero ou estiver faltando, a conta não tem resultado (divisão por zero), o que geraria `NaN`, `Infinity` ou o encerramento do app. No arquivo:
- **DEV-0311** tem `batteryCapacityMah` = 0.
- **DEV-0745** tem `screenOnMinutes` = 0.

O código também trata casos que não aparecem no arquivo atual, mas que a especificação admite: capacidade **ausente** e registro em mWh **sem** `nominalVoltage`.

### Decisão
Registros incompletos são classificados como **`INDETERMINADO`** e aparecem na lista com o rótulo **"SEM DADOS"**. O dreno **não é calculado** para eles, e nenhum valor é inventado.

### Raciocínio
Classifico como INDETERMINADO porque preservo a contagem total da frota (exigida no REQ-05), garanto a robustez do app diante de dados corrompidos e dou visibilidade para a equipe de que aquele dispositivo tem um problema no envio de telemetria.

### Alternativas descartadas
- **Excluir o registro da lista:** a soma das contagens por status deixaria de ser igual ao total do arquivo (REQ-05), e um aparelho com problema de telemetria sumiria da vista da equipe.
- **Falhar na ingestão:** um único registro ruim impediria o app de mostrar todos os outros aparelhos, que estão corretos.

### Limitação reconhecida
Um aparelho que tem um dado faltando é `INDETERMINADO` mesmo que outros sinais dele (falhas ou jank) estejam ruins. Por exemplo, o DEV-0311 tem `jankFramesPct` = 11,2 e `crashCount7d` = 1, mas, por ter capacidade 0, aparece como "SEM DADOS" e não como `ATENCAO`.

---

## 4. Precisão numérica e regra de arredondamento

### Decisão
O cálculo de negócio não é alterado por questão visual. O domínio calcula a regra com precisão total (garantindo que os testes de fronteira passem na JVM) e a interface fica responsável só por formatar o texto bonito para o usuário.

### Consequência
A tela mostra o dreno com uma casa decimal. Por isso, um aparelho com dreno de, por exemplo, 11,96 %/h aparece na tela como "12,0 %/h", mas é classificado como `ATENCAO`, porque o valor exato (11,96) é menor que 12.

---

## 5. Justificativa com múltiplos sinais no mesmo nível

### Decisão
Mostrar todos os motivos que dispararam aquele status na justificativa textual.

### Raciocínio
Se um celular fica CRÍTICO tanto por dreno quanto por travamentos (jank), omitir um dos motivos é dar um diagnóstico incompleto. Se o técnico resolver apenas o dreno, o aparelho continuaria preso no status crítico pelo travamento sem a equipe entender o porquê.

### Regra
- A justificativa cita **todos os sinais do mesmo nível** que definiu o status, cada um com o **valor observado** e o **limiar**.
- Sinais de nível mais baixo não aparecem como motivo. Exemplo: um aparelho `CRITICO` por dreno que também tem 1 falha (sinal de `ATENCAO`) cita apenas o dreno.
- Para `INDETERMINADO`, a justificativa diz qual dado está faltando ou inválido.

---

## 6. Tensão nominal inválida

### Decisão
Tensão menor ou igual a zero é tratada como dado ausente → INDETERMINADO.

### Raciocínio
Tensão zero ou negativa é um absurdo físico que geraria erro de divisão por zero (Infinity). Tratar como INDETERMINADO evita que o app quebre e mantém a coerência de preservar a contagem total de itens da frota.

---

## 7. Ameaça à validade

Meu app converte mWh para mAh usando a tensão nominal informada por cada aparelho (entre 3,80 e 3,88 V no arquivo). Só que na vida real, uma bateria de lítio descarrega e a voltagem cai de 4,2V (cheia) para 3,4V (fraca). Se um celular funcionou muito tempo com a bateria quase acabando, a voltagem real foi menor do que a nominal. O meu app vai calcular um consumo menor do que o consumo real de verdade, e um celular que deveria estar em ATENÇÃO pode acabar recebendo um selo OK por um erro de aproximação física.

Essa ameaça afeta apenas os 10 registros em mWh, e pesa mais nos aparelhos com dreno próximo dos limiares de 7 e 12 %/h.