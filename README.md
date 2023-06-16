RPListening
============================

Overview
------------

RPListening is an Open Source desktop client for Roku private listening.

Supported Operating Systems:
* Mac OS
* Linux
* Windows (in the future)

The latest (beta) release can be found [**here**](https://github.com/wseemann/RPListening/releases/latest/).

Dependencies
------------

The following dependencies are required to run RPListening:
* Java 11.0.9 or later with JavaFX installed
* FFmpeg

Compiling
------------

    git clone https://github.com/wseemann/RPListening.git
    cd RPListening/RPListening
    ./gradlew customFatJar

The resulting jar file can be found in build/lib

Running
------------

RPListening can be run with a UI and without. To run with the application **with a UI** simply run the jar file
or execute the following command in a terminal:

    java -jar RPListening-1.1.jar

To run the application **without a UI**:

    java -jar RPListening-1.1.jar -i <Roku Device IP Address>

For example:

    java -jar RPListening-1.1.jar -i 192.168.1.64

If you don't know your devices IP address you can find it using the following command in a terminal:

    java -jar RPListening-1.1.jar -d


NOTE FROM ELSHNKHLL:

⚠️ Warning after following all instructions to the letter it didn't work on my MAC.
After pressing [Start] button 🎧 symbol apeeared on TV but no sound came of my speakers.
In order to make it work, i was forced to run FFPLAY on separate terminal manually, 
in a folder with the file roku.sdp containing following text.

v=0
o=- 0 0 IN IP4 127.0.0.1
s=-
c=IN IP4 127.0.0.1
m=audio 5153 RTP/AVP 97
a=rtpmap:97 opus/48000/2


Here is my command line:

ffplay -hide_banner -loglevel debug -protocol_whitelist pipe,file,udp,rtp -vn -nodisp -nostats -i roku.sdp




