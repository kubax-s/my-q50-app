# keys/

`obu_cert.pem` – **publiczny** certyfikat OBU z firmware InTouch (`CN=tt18002`, wystawca `IT5.YGOMI.COM CA`, RSA-1024, ważny do 2032-02-18).

Narzędzie `tools/epk.ts` szyfruje nim losowy klucz AES pakietu `.epk`; radio odszyfrowuje go swoim kluczem prywatnym zapisanym w firmware. Do budowania wystarcza część publiczna – **klucza prywatnego nie ma w tym repozytorium i nie jest potrzebny.**

Źródło: [bugjosh/appgarage-dash](https://github.com/bugjosh/appgarage-dash/tree/main/keys) (licencja MIT), zweryfikowany tam na Q60 Red Sport 2018. Działa na radiach z tą samą generacją firmware InTouch; inna wersja firmware może wymagać innego certyfikatu.
