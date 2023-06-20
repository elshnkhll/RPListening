cd ~/PROJECTS/RPListening-main/RPListening/
rm -rf build
./gradlew customFatJar
cp roku.sdp build/libs/roku.sdp
cd build/libs/
java -jar RPListening-1.1.jar
# ffplay -hide_banner -loglevel debug -protocol_whitelist pipe,file,udp,rtp -vn -nodisp -nostats -i roku.sdpffplay -hide_banner -loglevel debug -protocol_whitelist pipe,file,udp,rtp -vn -nodisp -nostats -i roku.sdp

