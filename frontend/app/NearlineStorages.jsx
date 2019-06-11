import React from 'react';
import axios from 'axios';
import {Bar} from 'react-chartjs-2';
import RefreshButton from "./common/RefreshButton.jsx";
import BytesFormatterImplementation from "./common/BytesFormatterImplementation.jsx";

//see https://stackoverflow.com/questions/2901102/how-to-print-a-number-with-commas-as-thousands-separators-in-javascript
function numberWithCommas(x) {
    return x.toString().replace(/\B(?=(\d{3})+(?!\d))/g, ",");
}

class NearlineStorages extends React.Component {
    static COUNT_MODE=1;
    static SIZE_MODE=2;

    constructor(props){
        super(props);

        this.state = {
            loading: false,
            lastError: null,
            storageData: [],
            countPoints: {},
            sizePoints: {},
            mode: NearlineStorages.SIZE_MODE
        }
    }

    static makeColourValues(count, offset){
        let values = [];
        for(let n=0;n<count;++n){
            let hue = (n/count)*360.0 + offset;
            values[n] = 'hsla(' + hue + ',75%,50%,0.6)'
        }
        return values;
    }

    static colourValues = NearlineStorages.makeColourValues(5, 10);


    componentWillMount() {
        this.refresh();
    }

    processData(){
        let updatedCountPoints = {};
        let updatedSizePoints = {};

        this.state.storageData.map(storageEntry=>{
            storageEntry.states.map(storageState=>{
                if(updatedCountPoints.hasOwnProperty(storageState.state)) {
                    updatedCountPoints[storageState.state] = updatedCountPoints[storageState.state].concat(storageState.count);
                } else {
                    updatedCountPoints[storageState.state] = [storageState.count];
                }

                if(updatedSizePoints.hasOwnProperty(storageState.state)) {
                    updatedSizePoints[storageState.state] = storageState.totalSize < 0 ? updatedSizePoints[storageState.state].concat(0) : updatedSizePoints[storageState.state].concat(storageState.totalSize);
                } else {
                    updatedSizePoints[storageState.state] = storageState.totalSize < 0 ? [0] : [storageState.totalSize];
                }

            })
        });

        this.setState({countPoints: updatedCountPoints, sizePoints: updatedSizePoints});
    }

    refresh(){
        this.setState({loading: true}, ()=>axios.get("/api/nearline/currentState").then(response=>{
            this.setState({loading: false, storageData: response.data}, ()=>this.processData());
        }).catch(err=>{
            this.setState({loading: false, lastError: err})
        }))
    }

    dataForMode(stateLabel){
        switch(this.state.mode){
            case NearlineStorages.COUNT_MODE:
                return this.state.countPoints[stateLabel];
            case NearlineStorages.SIZE_MODE:
                return this.state.sizePoints[stateLabel];
            default:
                console.error("Didn't recognise mode ", this.state.mode);
                return null;
        }
    }

    labelForMode(rawValue){
        switch(this.state.mode){
            case NearlineStorages.COUNT_MODE:
                return numberWithCommas(rawValue);
            case NearlineStorages.SIZE_MODE:
                return BytesFormatterImplementation.getValueAndSuffix(rawValue);
            default:
                console.error("Didn't recognise mode ", this.state.mode);
                return null;
        }
    }

    render(){
        return <div className="container">
            <span className="controls-banner">
                <RefreshButton isRunning={this.state.loading} clickedCb={()=>this.refresh()}/>
                <select value={this.state.mode} onChange={evt=>{
                    console.log("New value", parseInt(evt.target.value));
                    this.setState({mode: parseInt(evt.target.value)})
                }}>
                    <option value={NearlineStorages.SIZE_MODE}>View total data size</option>
                    <option value={NearlineStorages.COUNT_MODE}>View file count</option>
                </select>
            </span>
            <Bar
                data={{
                    labels: this.state.storageData.map(entry=>entry.storage),
                    datasets: Object.keys(this.state.sizePoints).map((stateLabel,idx)=>{
                        return {
                            label: stateLabel,
                            backgroundColor: NearlineStorages.colourValues[idx],
                            data: this.dataForMode(stateLabel)
                        }
                    })
                }}
                options={{
                    title: {
                        display: true,
                        label: "Nearline storage sizes",
                        fontSize: 24
                    },
                    tooltips: {
                       callbacks: {
                           label: (tooltipItem,data)=>{
                               let xLabel, yLabel;

                                try {
                                    const result = this.labelForMode(tooltipItem.yLabel);
                                    yLabel = result[0] + result[1] ? result[1] : "";
                                    xLabel = data.datasets[tooltipItem.datasetIndex].label;
                                    return xLabel + ": " + yLabel;
                                } catch(err){
                                    console.error(err);
                                    return tooltipItem.xLabel + ": " + tooltipItem.yLabel;
                                }
                           }
                       }
                    },
                    scales: {
                        yAxes: [{
                           scaleLabel: {
                               display: true,
                               labelString: "Size"
                           },
                            stacked: true,
                            ticks: {
                               callback: (value,index,series)=>{
                                   const result = this.labelForMode(value);
                                   return result[0] + result[1];
                               }
                            }
                        }],
                        xAxes: [{
                            stacked: true,
                        }]
                    },
                    legend: {
                        display: true,
                        position: "bottom"
                    }
                }}
                />
        </div>
    }
}

export default NearlineStorages;