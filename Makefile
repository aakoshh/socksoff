
build-sockslib:
	cd ../sockslib && mvn clean install

build-server:
	sbt server/pack

start-server:
	./server/target/pack/bin/server

test-curl:
	curl -Is --socks5-hostname 127.0.0.1:1080 http://example.com

start-browser:
	google-chrome --proxy-server="socks5://localhost:1080" --host-resolver-rules="MAP * 0.0.0.0 , EXCLUDE localhost"