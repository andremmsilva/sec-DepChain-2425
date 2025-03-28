// SPDX-License-Identifier: UNKNOWN 
pragma solidity ^0.8.26;

import "@openzeppelin/contracts/token/ERC20/ERC20.sol";
import "./blacklist.sol";

contract ISTCoin is ERC20, Blacklist {

    constructor() ERC20("ISTCoin", "IST"){ 
            _mint(msg.sender, 100000);
    }

    function decimals() public view virtual override returns (uint8) {
        return 2;
    }

    function transferTo(){

    }

    function transferFrom(){

    }

    function balance(){
        
    }
}