package services

import (
	"crypto/rsa"
	"math/big"
)

// buildRSAPubKey constructs an *rsa.PublicKey from modulus and exponent.
func buildRSAPubKey(n *big.Int, e int) *rsa.PublicKey {
	return &rsa.PublicKey{N: n, E: e}
}
