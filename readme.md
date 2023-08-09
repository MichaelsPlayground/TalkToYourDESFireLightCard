# Talk to your Mifare DESFire EV1/EV2/EV3 card

This is a sample app to demonstrate how to work with a Mifare DESFire EV1/EV2/EV3 card. 

For simplicity this app uses a DESFire tag with **factory settings** means:

- it uses **Plain communication** only (no MACed or Enciphered Communication)
- it shows how to work with **Standard Files** only (no Backup, Value, Linear Record, Cyclic Record or TransactionMAC Files)
- there are 2 **Access modes** hardcoded: **Free Access** (use without any key) and **Key Secured Access** with 5 predefined keys
  (0 = Application Master Key, 1 = Read & Write access key, 2 = Change Access Rights key, 3 = Read access key and 4 = Write access key)
- it works with a predefined **Application Identifier** (AID) of "D1D2D3"
- the  Standard files have a hardcoded size of 32 bytes
- the app is working with **DES keys** only and the **Master Application KEY** and it's **Key Settings** remain unchanged to prevent from any damage to the card

As the **Authentication** with a key is essentially for a successful transaction there is a huge amount of code lines taken from another 
project. I copied all necessary code from the **NFCJLIB project** available here: https://github.com/andrade/nfcjlib which is provided 
by **Daniel Andrade**, thanks a lot for his contribution. Please obey the LICENCE here: https://github.com/andrade/nfcjlib/blob/master/LICENSE.

The only 'official' information's on DESFire EVx cards can be found here (yes, you understand it right - 'official' and useful 
documentation is available only on another card type, the DESFire Light tag): 

Data sheet – MIFARE DESFire Light: https://www.nxp.com/docs/en/data-sheet/MF2DL_H_x0.pdf

Application note – AN12343 MIFARE DESFire Light Features and Hints: https://www.nxp.com/docs/en/application-note/AN12343.pdf

Leakage Resilient Primitive (LRP) Specification: https://www.nxp.com/docs/en/application-note/AN12304.pdf (test vectors)

Symmetric key diversification's: https://www.nxp.com/docs/en/application-note/AN10922.pdf

System level security measures for MIFARE installations: https://www.nxp.com/docs/en/application-note/AN10969.pdf

For differences between Mifare DESFire EVx versions see: MIFARE DESFire EV3 contactless multi-application IC MF3DHx3_SDS.pdf (page 5)

DESFire protocol (overview about DESFire EV1 commands): https://github.com/revk/DESFireAES/blob/master/DESFire.pdf

NTAG 424 DNA NT4H2421Gx.pdf: https://www.nxp.com/docs/en/data-sheet/NT4H2421Gx.pdf

NTAG 424 DNA and NTAG 424 DNA TagTamper features and hints AN12196.pdf: https://www.nxp.com/docs/en/application-note/AN12196.pdf

NFCJLIB library: https://github.com/andrade/nfcjlib

Type of messaging:
- plain communication
- MACed communication
- fully enciphered communication using DES, TDES or AES keys
- AES Secure Messaging
- LRP Secure Messaging (Leakage Resilient Primitive)

This app always uses ISO/IEC 7816-4 wrapped comands.  

Mifare type identification procedure AN10833.pdf

Note: a 0x9D error ('Permission denied') may occur when sesTMC reached its maximal value or TMCLimit was reached. 

```plaintext
However, I can provide you with the following information about the "SET CONFIGURATION" command:

The command is used to configure the settings of a Mifare DESFire EV3 card.
The command has the following format:
SET CONFIGURATION <option> <value>
The <option> field specifies the setting to be configured.
The <value> field specifies the value for the setting.
The following table lists the possible options for the <option> field:

Option	Description
01	Enable or disable the transaction timer.
02	Set the value of the transaction timer.
03	Enable or disable the access control feature.
04	Set the value of the access control key.


The value for enabling the transaction timer is 0x01. The value for disabling the transaction timer is 0x00.

enable: private static final byte[] SET_CONFIGURATION_COMMAND = {0x00, 0x03, 0x01, 0x01};
disable: private static final byte[] SET_CONFIGURATION_COMMAND = {0x00, 0x03, 0x01, 0x00};

```

Mifare® Application Programming Guide for DESFire (2011): https://www.cardlogix.com/wp-content/uploads/MIFARE-Application-Programming-Guide-for-DESFfire_rev.e.pdf


in DesfireAuthenticateEv2:
public boolean changeFileSettingsNtag424Dna(byte fileNumber) {
status: working
eventually the file needs to get the sdm options on setup even if disabled
todo check with real tag if fileSettings are "prepared" for SDM usage
see page 4 of video/slideshow https://www.slideshare.net/NXPMIFARETeam/secure-dynamic-messaging-feature
"The SDM feature is enablement is done during the creation of the NDEF file, a Standard Data File inside the Mifare DESFire application"

decryption of encrypted PICC data, decryption of encrypted File data and MAC verification works on SDM message


# Activation of Secure Dynamic Messaging (SDM) for Secure Unique NFC (SUN) feature

The SDM/SUN feature is available on Mifare DESFire EV3 card types only. It is very useful if your business case is to work 
with a "standard" reader infrastructure that are smartphones (Android or Apple) without usage of a dedicated app installed 
on the phone.

## What is a SDM/SUN message ?

As you can format (parts of) a Mifare DESFire tag in NDEF mode the tag will respond to an attached reader with the data that is 
stored in the NDEF data file. There are several NDEF message types available but the SDM/SUN feature uses the **URL record** 
where an URL is stored that points to a backend server. When the tag is tapped to a smartphone an (installed) application will 
open that is capable of working with URL data, usually your browser will will open and tries to connect to the URL provided by 
the tag.

The backend server can verify the data provided by the link and act on the data, e.g. open a door or buy a transport ticket.

## How does SDM work ?

Below you find a sample **URL** that points to a (backend) server: 

https://sdm.nfcdeveloper.com/

When using this link you get some information about a "Secure Dynamic Messaging Backend Server Example" that can be used for 
NTAG 424 DNA tags but for DESFire EV3 as well but, beware, when you carefully read the examples you may find that the full 
URL looks like 

https://sdm.nfcdeveloper.com/tag?picc_data=EF963FF7828658A599F3041510671E88&cmac=94EED9EE65337086

so the "real" endpoint ("**Base URL**") is something like 

https://sdm.nfcdeveloper.com/tag

followed by data fields like "uid", "ctr", "picc_data" or "cmac".

That brings us to the **Template URL** that could look like this URL:

https://sdm.nfcdeveloper.com/tag?picc_data=00000000000000000000000000000000&cmac=0000000000000000

If you use the template URL on the backend server you will receive a "400 Bad Request: Invalid message (most probably wrong signature)" error. 
That is due to the fact that this template URL does not contain any real data - they would be in the "00"er fields that act as a 
placeholder for real data.

If you write the URL using a NDEF message to the NDEF file a tapped device will open the browser, connects to the backend server and - 
nothing will happen as the SDM feature is not enabled so far.

## How to enable SDM on a Mifare DESFire EV3 tag ?

To make it very short, you tell the tag that from now on the SDM feature is enabled and the tag should provide data like the UID and the 
reader counter as part of the link. When tapping the tag to a reader device the tag will copy the requested "real data" into the 
placeholder positions so that the URL will look like this:

https://sdm.nfcdeveloper.com/tagpt?uid=041E3C8A2D6B80&ctr=000006&cmac=4B00064004B0B3D3

Using this URL the backend server will respond like this:

```plaintext
Cryptographic signature validated.
Encryption mode: AES
NFC TAG UID: 041e3c8a2d6b80
Read counter: 6
```

If the door opener acts on a "white list with approved UID's" the door could get open now.

This is an bad example because we are sending confidential data like the card's UID over an insecure infrastructure and we 
should change the "Plain" data transmission to an "Encrypted" one.

## How to change the transmission from "Plain" to "Encrypted" mode

The advantage of a Plain transmission is that we do not need anything special like "encryption keys" or "algorithms" 
to run the  transmission but the disadvantage is: everyone can read out the (confidential) data. For that reason 
the DESFire EV3 tag supports the "Encrypted" mode that needs an additional parameter. As "Encrypted" data needs to get decrypted 
both parties need to agree on an **encryption key** that is used for encryption and decryption ("symmetric encryption").

On **creation of an application** on a DESFire tag you setup up to 14 keys that can act for several purposes. When **creating 
a file** you define which key is used for a dedicated purpose (in most times it is an access right like "read" or "write"). For 
Encrypted SDM features you define a well one of those keys as encryption keys and the backend server needs to know this specific 
key for decryption.








