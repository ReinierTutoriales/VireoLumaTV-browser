# Repository audits

Start with the [current consolidated audit](CONSOLIDATED_AUDIT_20261007.md)
and [current work plan](../ROADMAP.md). They distinguish executed checks from
pending Android builds and physical-device validation.

The October 5 topic audits remain evidence for their own source revisions, not
instructions to reapply their patches. The previous work plan is preserved in
[the October 7 archive](archive-2026-10-07/ROADMAP_PRE_CONSOLIDATION.md).

## Avance verificable sin dispositivo

[Restauración y User-Agent](OFF_DEVICE_FIXES_20261007.md): evidencia de fallos
previos, correcciones aisladas, cobertura de CI y límites de la validación.

## Android TV y recursos limitados

La [auditoría contra documentación oficial](ANDROID_TV_LOW_RESOURCE_AUDIT_20261007.md)
examina el código `569cdda`: nueve defectos de flujo y ocho riesgos o brechas
pendientes de medición física. Es el punto de partida para las siguientes
correcciones en la misma rama.

## Archived license and rebranding review

These two files preserve the full October 1, 2026 license/rebranding audit and matching reference inventory produced on branch `audit/vireo-public-links` before the VireoLumaTV package migration. Their original findings remain useful as evidence, but refer to base commit `c339fcf` and must not be treated as a current defect list.

The consolidated brand, attribution, identity, privacy, distribution notices and license files are in the current repository root and `licenses/`. Resolved items include VireoLumaTV labels/assets/links, store screenshot removal, the package/application-ID migration, bundled license notices and disabling inherited update channels. The exact transitive release dependency inventory, device privacy behavior, artwork/mark clearance and device testing require their own follow-up.

Original proposal PR: https://github.com/ReinierTutoriales/VireoLumaTV-browser/pull/49
