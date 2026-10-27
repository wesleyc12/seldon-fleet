# PROTOCOLO.md: Seldon Fleet

> Protocolo de decisões do desafio Seldon Fleet (REQ-10).
> As **seções 1 a 7** foram escritas e versionadas **antes da implementação do código** (commit `fac1fed`, 25/10/2026), como pré-registro do critério de classificação (seções 3.3 e 4.4 da especificação).
> As **seções 8 e 9** foram acrescentadas em 27/10/2026, **depois** da implementação. Elas **não alteram a regra**: registram decisões tomadas durante o código, a conferência com o arquivo real e a autoria das decisões.
> Objetivo: quem ler apenas este arquivo deve conseguir prever o status que o app atribui a qualquer registro do `frota.json`.

**Autor:** Wesley de Castro Silva
**Data do pré-registro:** 25/10/2026
**Data dos complementos (seções 8 e 9):** 27/10/2026

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

---

## 8. Complementos (27/10/2026, escritos depois da implementação)

> Esta seção foi escrita **depois** do código. Ela **não muda a regra** das seções 1 a 6: só registra decisões que apareceram durante a implementação e a conferência com o arquivo real.

### 8.1 Receita rápida: como prever o status de um registro
Resumo das seções 1 a 6, com o complemento 8.2. Siga os passos em ordem:

1. **Energia em mAh:**
    - se `energyUnit` = `"mAh"`, use o valor como está;
    - se `energyUnit` = `"mWh"` e `nominalVoltage` > 0, divida pela tensão;
    - em qualquer outro caso, **não há energia válida**.
2. **INDETERMINADO ("SEM DADOS")** se: capacidade ausente ou ≤ 0, **ou** `screenOnMinutes` ≤ 0, **ou** não há energia válida. Pare aqui.
3. **Calcule o dreno relativo:** `energiaMah ÷ (screenOnMinutes ÷ 60) ÷ capacidade × 100`, em %/h.
4. **CRÍTICO** se: dreno ≥ 12 **ou** `crashCount7d` ≥ 3 **ou** `jankFramesPct` ≥ 15. Pare aqui.
5. **ATENÇÃO** se: dreno ≥ 7 **ou** `crashCount7d` ≥ 1 **ou** `jankFramesPct` ≥ 8. Pare aqui.
6. Senão, **OK**.

### 8.2 Unidade de energia desconhecida → INDETERMINADO
- **Decisão:** só as grafias **exatas** `"mAh"` e `"mWh"` são aceitas. Qualquer outra (por exemplo `"MWh"`, `"mwh"` ou `"kWh"`) deixa o registro **sem energia válida**, e ele vira `INDETERMINADO` ("SEM DADOS"), com a justificativa dizendo que falta a energia.
- **Raciocínio:** no SI, **m** minúsculo é *mili* e **M** maiúsculo é *mega*. `"MWh"` seria megawatt-hora, um bilhão de vezes maior. Aceitar grafias "parecidas" poderia gerar um número falso **em silêncio**, o que o REQ-03 proíbe. Já "SEM DADOS" é um erro **visível**, e a contagem total da frota continua a mesma.
- **No arquivo atual:** todas as 30 grafias são exatas (20 `mAh` e 10 `mWh`), então nenhum registro cai neste caso.

### 8.3 Arquivo ilegível ou fora do contrato → mensagem de erro na tela
Existem **dois níveis** de "dado errado":

| O que veio errado | Exemplo | O que o app faz |
|---|---|---|
| **Um registro** com defeito previsto | capacidade 0 ou ausente, tela 0, mWh sem tensão, unidade desconhecida | Só aquele aparelho vira `INDETERMINADO` ("SEM DADOS"). Os outros aparecem normalmente. |
| **O arquivo inteiro** fora do contrato do PDF | JSON quebrado, ou faltando um campo que a especificação garante (`id`, `model`, ...) | **Mensagem de erro** na tela. Nenhum aparelho aparece, e o app não fecha. |

- **Campos que podem faltar:** só `batteryCapacityMah` e `nominalVoltage`, os dois que a especificação (seção 3.2) diz que podem estar ausentes. Os outros são garantidos pelo contrato do PDF; se faltarem, é o arquivo inteiro que está fora do combinado.
- **Raciocínio:** uma **lista vazia** daria a entender que a frota não tem nenhum aparelho, quando na verdade não foi possível ler o arquivo. Uma mensagem de erro explícita diz a verdade ao usuário.
- **No arquivo atual:** os 30 registros têm todos os campos garantidos, então o app nunca mostra essa mensagem com o `frota.json` oficial.

### 8.4 Texto da justificativa: "(limiar: X)" e não "acima do limiar"
- **Formato adotado:** `Crítico: dreno de 13,4 %/h (limiar: 12 %/h).`, com uma linha por sinal quando há mais de um (seção 5).
- **Raciocínio:** a regra usa **≥**. Um aparelho com dreno de **exatamente** 12,0 %/h é `CRITICO`, e a frase "acima do limiar de 12 %/h", do exemplo da especificação, seria falsa para ele. "(limiar: 12 %/h)" é verdadeiro nos dois casos.

### 8.5 Ordem da conta no código
O código calcula `energiaMah × 100 ÷ capacidade ÷ horas de tela`, ou seja, **multiplica por 100 antes de dividir pela capacidade**. O resultado é matematicamente igual à fórmula da seção 1. Fiz assim porque o computador guarda decimais em binário: dividir antes pode transformar 7,0 em 7,000000000000001. Multiplicando antes, os valores de fronteira (7,0 e 12,0) ficam exatos, e os testes de fronteira testam o ≥ de verdade.

### 8.6 Nota sobre a ameaça à validade (seção 7)
O erro da conversão pode ir para **os dois lados**:
- **Tensão real menor que a nominal** (bateria fraca, até ~3,4 V): o app calcula um consumo **menor** que o real, até cerca de **12 % a menos**. Um aparelho que deveria estar em `ATENCAO` pode sair `OK`. Esse é o caso da seção 7.
- **Tensão real maior que a nominal** (bateria cheia, até ~4,2 V): o app calcula um consumo **maior** que o real, até cerca de **9 % a mais**. Um aparelho `OK` pode sair `ATENCAO`.

Esses percentuais são o pior caso, com o aparelho funcionando o tempo todo num extremo. Como o arquivo não informa a tensão ao longo do uso, não é possível saber para qual lado errou cada aparelho.

### 8.7 Conferência com o arquivo real (feita depois do código; a regra não mudou)

| Registro | Conta | Status no app |
|---|---|---|
| DEV-0137 (mAh) | 6,2 %/h, mas 2 falhas | `ATENCAO` |
| DEV-0204 (mWh) | 3773 ÷ 3,85 = 980 mAh → 6,3 %/h | `OK` |
| DEV-0522 (mWh) | 1585,7 mAh convertidos ÷ (142 ÷ 60) = 670 mAh/h → 670 ÷ 5000 × 100 = 13,4 %/h | `CRITICO` |
| DEV-0311 | capacidade 0 | `INDETERMINADO` |
| DEV-0745 | tela 0 | `INDETERMINADO` |

**Total da frota: 16 `OK` · 8 `ATENCAO` · 4 `CRITICO` · 2 `INDETERMINADO` = 30.**

Conferi esse total de três formas independentes: um script Python à parte aplicando esta regra, o teste automático `FleetFileTest` (que lê o arquivo real) e a tela de lista no celular.

---

## 9. Autoria das decisões e uso de IA

### Decisões minhas
- **As seis decisões deste protocolo**, cada uma com o meu raciocínio:
    1. a regra de referência;
    2. INDETERMINADO para registros incompletos;
    3. arredondar só na tela;
    4. citar todos os sinais do mesmo nível;
    5. tensão ≤ 0 tratada como ausente;
    6. a ameaça à validade da tensão nominal.
- **As decisões tomadas durante a implementação**, registradas no meu diário de projeto:
    - **Camada de dados:** capacidade 0 julgada pela regra; unidade desconhecida; arquivo ilegível; testar o arquivo real antes da tela.
    - **Lista:** filtro único com "Todos"; ordem do arquivo; onde montar as peças; até onde o filtro sobrevive; testar com repositório falso; chip com cor e texto.
    - **Detalhe:** biblioteca de navegação; ViewModel próprio; texto da justificativa; formato da data.
    - **Favoritos:** contrato no domínio; a lista escuta o arquivo salvo; estrela só na lista.

### Onde a IA (Claude) ajudou
- Explicou os conceitos e apresentou as opções de cada decisão, com prós, contras e uma recomendação. Em algumas decisões, escolhi diferente da recomendação.
- Organizou o texto deste protocolo no formato final, mantendo o meu raciocínio.
- Escreveu o código a partir das minhas decisões.

### O que eu fiz e verifiquei
- Criei cada arquivo e rodei os testes e o lint.
- Fiz o teste de mutação: troquei o limiar de 12 para 13 e os testes falharam.
- Conferi no celular a lista, o filtro, o detalhe, o voltar com o filtro mantido e os favoritos depois de fechar o app.