# Build the SOCKS5 library we're using and register it into the local .m2 repo.
build-sockslib:
	cd ../sockslib && mvn clean install

# Make an executable to experiment with.
build-server:
	sbt server/pack

# Build the SMS SOCKS5 library to be used in Android.
build-sms:
	sbt '; sms/compile ; sms/publishLocal'

# Test that a normal SOCKS5 server actually works with the `try-curl` function.
start-server:
	./server/target/pack/bin/server

test-curl:
	curl -Is --socks5-hostname 127.0.0.1:1080 http://example.com

start-browser:
	google-chrome --proxy-server="socks5://localhost:1080" --host-resolver-rules="MAP * 0.0.0.0 , EXCLUDE localhost"