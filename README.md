# PowerBLE-Smart

Base Capacitor/Android para PowerBLE Smart.

## Recursos nativos preparados

- GPS Android
- BLE Central
- Cycling Speed & Cadence (CSC 0x1816)
- Heart Rate (0x180D)
- Cycling Power (0x1818)
- BLE Peripheral / FTMS (0x1826)
- Interface JavaScript
- GitHub Actions para APK debug

## Importante

O parser CSC incluído é uma base de integração. Para exibir RPM real com precisão, o estado anterior de crank revolutions e event time deve ser mantido entre notificações e convertido para RPM. O FTMS também deve ser validado contra o perfil FTMS completo e contra o dispositivo cliente (Zwift/Rouvy/Bryton).

## Termux

Copie/extrate este projeto em:
`~/storage/downloads/PowerBLE-Smart`

Depois:

`bash TERMUX-ENVIAR.sh`

O GitHub Actions gerará o APK em Artifacts.
