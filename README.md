# oficina-auth-function

Function Serverless (Cloud Functions) do Tech Challenge Fase 3 (Pós-Tech
SOAT).

Um dos 4 repositórios exigidos pelo desafio. Hospeda as functions que:

- Validam o CPF do cliente, consultam sua existência/status na base e emitem
  um JWT para consumo das APIs protegidas da aplicação principal
  ([`oficina`](https://github.com/robsonago/oficina));
- Consomem eventos de notificação (ex.: e-mail de atualização de ordem de
  serviço) publicados pela aplicação principal.

> Repositório em construção — este README será substituído pela versão final
> com stack, instruções de execução/deploy e link do ambiente ativo assim que
> as functions forem implementadas.

Dockerfile normalmente não se aplica a Cloud Functions.
