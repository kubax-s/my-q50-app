# keys/

`obu_cert.pem` – the **public** OBU certificate from the InTouch firmware (`CN=tt18002`, issuer `IT5.YGOMI.COM CA`, RSA-1024, valid until 2032-02-18).

`tools/epk.ts` uses it to encrypt the random AES key of each `.epk` package; the head unit decrypts it with its private key stored in the firmware. Building needs only the public part – **the private key is not in this repository and is not needed.**

Source: [bugjosh/appgarage-dash](https://github.com/bugjosh/appgarage-dash/tree/main/keys) (MIT license), verified there on a 2018 Q60 Red Sport. It works on head units with the same InTouch firmware generation; other firmware versions may need a different certificate.
