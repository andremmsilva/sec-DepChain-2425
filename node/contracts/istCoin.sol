// SPDX-License-Identifier: UNKNOWN 
pragma solidity ^0.8.26;

import "@openzeppelin/contracts/token/ERC20/ERC20.sol";
import "./blacklist.sol";

contract ISTCoin is ERC20, Blacklist {
    constructor() ERC20("IST Coin", "IST") {
        _mint(msg.sender, 100_000_000 * 10**2); // 100 million IST with 2 decimals
    }

    function transfer(address recipient, uint256 amount) public override returns (bool) {
        require(!isBlacklisted(msg.sender), "Sender is blacklisted");
        require(!isBlacklisted(recipient), "Recipient is blacklisted");
        return super.transfer(recipient, amount);
    }

    function transferFrom(address sender, address recipient, uint256 amount) public override returns (bool) {
        require(!isBlacklisted(sender), "Sender is blacklisted");
        require(!isBlacklisted(recipient), "Recipient is blacklisted");
        return super.transferFrom(sender, recipient, amount);
    }
}