// SPDX-License-Identifier: UNKNOWN 
pragma solidity ^0.8.26;

import "@openzeppelin/contracts/token/ERC20/ERC20.sol";
import "@openzeppelin/contracts/access/Ownable.sol";

contract Blacklist is Ownable {
    constructor() Ownable(msg.sender) {} // Explicitly calling the Ownable constructor
    mapping(address => bool) private blacklisted;

    event AddedToBlacklist(address indexed account);
    event RemovedFromBlacklist(address indexed account);

    function addToBlacklist(address account) external onlyOwner returns (bool) {
        require(!blacklisted[account], "Account already blacklisted");
        blacklisted[account] = true;
        emit AddedToBlacklist(account);
        return true;
    }

    function removeFromBlacklist(address account) external onlyOwner returns (bool) {
        require(blacklisted[account], "Account is not blacklisted");
        blacklisted[account] = false;
        emit RemovedFromBlacklist(account);
        return true;
    }

    function isBlacklisted(address account) public view returns (bool) {
        return blacklisted[account];
    }
}