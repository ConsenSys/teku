#!/bin/sh


CLIENT=$1

if [ "$CLIENT" == "artemis" ]
then
    SCRIPT_DIR=$(dirname $0)
    source $SCRIPT_DIR/run_utils.sh
    rm -rf ./demo
    mkdir -p ./demo
    rm -f ../config/runConfig.*
    configure_node "mothra" 0 1 "../config/config.toml"
    sh configurator.sh "../config/runConfig.0.toml" active true
    sh configurator.sh "../config/runConfig.0.toml" genesisTime 1567570251
    sh configurator.sh "../config/runConfig.0.toml" ownedValidatorStartIndex 0
    sh configurator.sh "../config/runConfig.0.toml" ownedValidatorCount 4
    sh configurator.sh "../config/runConfig.0.toml" numValidators 4
    sh configurator.sh "../config/runConfig.0.toml" numNodes 1
    cd $SCRIPT_DIR/demo/node_0/ && ./artemis --config=./config/runConfig.0.toml --logging=INFO

elif [ "$CLIENT" == "lighthouse-node" ]
then

    export BOOTNODE_ENR=$(cat ~/.mothra/network/enr.dat)
    export LISTEN_ADDRESS=127.0.0.1
    export PORT=19001

    export DIR=$HOME/projects/consensys/pegasys/lighthouse/lighthouse/target/release
    export EXE=beacon_node

    export VALIDATOR_COUNT=8
    export GENESIS_TIME=1567570251  #$((`date +%s`))

    # Start lighthouse
    # export RUST_LOG=libp2p_gossipsub=debug

    rm -rf ~/.lighthouse
    cd $DIR && ./beacon_node --boot-nodes $BOOTNODE_ENR --listen-address $LISTEN_ADDRESS --port $PORT testnet -r quick $VALIDATOR_COUNT $GENESIS_TIME


    #tmux new-session -d -s foo 'cd $DIR && ./beacon_node --boot-nodes $BOOTNODE_ENR --listen-address $LISTEN_ADDRESS --port $PORT testnet -r quick $VALIDATOR_COUNT $GENESIS_TIME'
    #tmux split-window -v -t 0 'cd $HOME/projects/consensys/pegasys/lighthouse/lighthouse/target/release && ./validator_client testnet -b insecure 0 8'
    #tmux select-layout tile
    #tmux rename-window 'lighthouse'
    #tmux attach-session -d

elif [ "$CLIENT" == "lighthouse-validator" ]
then
    export DIR=$HOME/projects/consensys/pegasys/lighthouse/lighthouse/target/release
    export VALIDATOR_COUNT=8
    cd $DIR  && ./validator_client testnet -b insecure 0 $VALIDATOR_COUNT
fi

