.PHONY: generate open clean lint

generate:
	xcodegen generate

open: generate
	open ChargeShare.xcodeproj

lint:
	swiftlint --strict

clean:
	rm -rf ChargeShare.xcodeproj
	rm -rf build/
