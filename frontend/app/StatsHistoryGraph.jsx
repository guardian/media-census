import React from "react";
import axios from "axios";
import PropTypes from "prop-types";
import LoadingThrobber from "./common/LoadingThrobber.jsx";
import ErrorViewComponent from "./common/ErrorViewComponent.jsx";
import {Scatter} from 'react-chartjs-2';
import RefreshButton from "./common/RefreshButton.jsx";
import moment from 'moment';

//see https://stackoverflow.com/questions/2901102/how-to-print-a-number-with-commas-as-thousands-separators-in-javascript
function numberWithCommas(x) {
    return x.toString().replace(/\B(?=(\d{3})+(?!\d))/g, ",");
}

class StatsHistoryGraph extends React.Component {
    constructor(props){
        super(props);

        this.state = {
            loading: false,
            lastError: null,
            startingTimestamp: null,
            finishingTimestamp: null,
            facetData: []
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

    static colourValues = StatsHistoryGraph.makeColourValues(5, 10);

    componentWillMount() {
        this.reload();
    }

    objectToQueryString(params) {
        if(!params || Object.keys(params).length===0) return "";
        const joinedString = Object.keys(params).map(k=>k + "=" + params[k]).join("&");
        return "?" + joinedString;
    }

    reload(){
        return new Promise((resolve, reject)=> {
            let urlParams = {};
            if (this.state.startingTimestamp) urlParams.startAt = this.state.startingTimestamp;
            if (this.state.finishingTimestamp) urlParams.endAt = this.state.finishingTimestamp;

            const url = "/api/jobs/forTimespan" + this.objectToQueryString(urlParams);

            this.setState({loading: true, lastError: null}, () => axios.get(url).then(result => {
                console.log(result.data);
                const facetData = result.data.entries.map(entry=>{return {
                    label: entry.scanStart,
                    epoch: moment(entry.scanStart).unix(),
                    noBackupsCount: entry.noBackupsCount,
                    partialBackupsCount: entry.partialBackupsCount,
                    fullBackupsCount: entry.fullBackupsCount
                }});

                this.setState({
                    loading: false,
                    lastError: null,
                    facetData: facetData,
                }, resolve());
            }).catch(err => {
                console.error(err);
                this.setState({loading: false, lastError: err}, reject(err));
            }))
        });
    }

    render(){
        return <div className="container">
            <span className="controls-banner"><RefreshButton isRunning={this.state.loading} clickedCb={()=>this.reload()}/></span>
            <Scatter
                data={{
                        labels: this.state.facetData.map(entry=>entry.label),
                        datasets: [{
                            label: "No backups",
                            backgroundColor: StatsHistoryGraph.colourValues[0],
                            borderColor: StatsHistoryGraph.colourValues[0],
                            showLine: true,
                            fill: true,
                            data: this.state.facetData.map(entry=>{return {x: entry.epoch, y: entry.noBackupsCount}})
                        },{
                            label: "Unimported",
                            backgroundColor: StatsHistoryGraph.colourValues[1],
                            borderColor: StatsHistoryGraph.colourValues[1],
                            showLine: true,
                            fill: true,
                            data: this.state.facetData.map(entry=>{return {x: entry.epoch, y: entry.unimportedCount}})
                        },{
                            label: "Unattached",
                            backgroundColor: StatsHistoryGraph.colourValues[2],
                            borderColor: StatsHistoryGraph.colourValues[2],
                            showLine: true,
                            fill: true,
                            data: this.state.facetData.map(entry=>{return {x: entry.epoch, y: entry.unattachedCount}})
                        },{
                            label: "Partial backups",
                            backgroundColor: StatsHistoryGraph.colourValues[3],
                            borderColor: StatsHistoryGraph.colourValues[3],
                            showLine: true,
                            fill: true,
                            data: this.state.facetData.map(entry=>{return {x: entry.epoch, y: entry.partialBackupsCount}})
                        }, {
                            label: "Full backups",
                            backgroundColor: StatsHistoryGraph.colourValues[4],
                            borderColor: StatsHistoryGraph.colourValues[4],
                            showLine: true,
                            fill: true,
                            data: this.state.facetData.map(entry=>{return {x: entry.epoch, y: entry.fullBackupsCount}})
                        }]

                }}
                options={{
                    elements: {
                        line: {
                            tension: 0
                        }
                    },
                    tooltips: {
                        callbacks: {
                            label: (tooltipItem, data)=>{
                                let xLabel,yLabel;

                                if(tooltipItem.xLabel){
                                    xLabel = moment(tooltipItem.xLabel*1000).format('dd Do HH:mm');
                                    yLabel = tooltipItem.yLabel ? numberWithCommas(tooltipItem.yLabel) : data.datasets[tooltipItem.datasetIndex].label;
                                } else {
                                    xLabel=tooltipItem.xLabel;
                                    yLabel = data.datasets[tooltipItem.datasetIndex].label;
                                }
                                return xLabel + ": " + yLabel;
                            }
                        }
                    },
                    title: {
                        display: true,
                        text: "Media State History",
                        fontSize: 24
                    },
                    scales: {
                        yAxes: [{
                            scaleLabel: {
                                display: true,
                                labelString: "File count",
                            },
                            stacked: true
                        }],
                        xAxes: [{
                            labelString: "Date",
                            ticks: {

                                callback: (value,index,series)=>{
                                    return moment(value*1000).format("dd Do MMM HH:mm:ss")
                                }
                            },
                            stacked: false
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

export default StatsHistoryGraph;