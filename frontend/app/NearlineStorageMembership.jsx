import React from 'react';
import axios from 'axios';
import {Pie,Bar} from "react-chartjs-2";
import BytesFormatterImplementation from "./common/BytesFormatterImplementation.jsx";
import moment from 'moment';
import RefreshButton from "./common/RefreshButton.jsx";
import {Link} from "react-router-dom";

class NearlineStorageMembership extends React.Component {

    static CHART_MODE_COUNT=1;
    static CHART_MODE_SIZE=2;

    constructor(props){
        super(props);

        this.state = {
            loading: false,
            lastError:null,
            totalCount:0,
            noMembership:0,
            totalSize:0,
            states:[],
            noMembershipTimeBreakdown: [],
            pieData: null,
            barData: null,
            chartMode: NearlineStorageMembership.CHART_MODE_COUNT
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

    static colourValues = NearlineStorageMembership.makeColourValues(10, 10);

    /**
     * updates pieData and barData based on the current chart mode and the data downloaded from the server
     */
    refreshChartData(){
        const fileStateDatapoints = this.state.states.map(entry=>this.state.chartMode===NearlineStorageMembership.CHART_MODE_COUNT ? entry.count : entry.totalSize);
        const fileStateLabels = this.state.states.map(entry=>entry.state);

        const timeBreakdownDatapoints = this.state.noMembershipTimeBreakdown.map(entry=>entry.doc_count);
        const timeBreakdownLabels = this.state.noMembershipTimeBreakdown.map(entry=>entry.date);

        this.setState({
            pieData: {
                datapoints: fileStateDatapoints,
                labels: fileStateLabels
            },
            barData: {
                datapoints: timeBreakdownDatapoints,
                labels: timeBreakdownLabels
            }
        })
    }

    refresh() {
        this.setState({loading: true},()=>axios.get("/api/nearline/membership").then(response=>{
            this.setState({
                loading: false,
                lastError: null,
                totalCount: response.data.totalCount,
                noMembership: response.data.noMembership,
                totalSize: response.data.totalSize,
                states: response.data.states,
                noMembershipTimeBreakdown: response.data.noMembershipTimeBreakdown
            }, ()=>this.refreshChartData())
        }).catch(err=>{
            console.error(err);
            this.setState({loading: false, lastError: err})
        }));
    }

    componentWillMount() {
        this.refresh();
    }

    render(){
        return <div>
            <span className="controls-banner">
                <RefreshButton isRunning={this.state.loading} clickedCb={()=>this.refresh()}/>
                <Link className="controls-banner-spacing" to="/nearlines">Nearline Stats</Link> |
                <Link className="controls-banner-spacing" to="/nearlines/membership">Nearline files without item membership</Link> |
                <select className="controls-banner-spacing" value={this.state.chartMode} onChange={evt=>this.setState({chartMode: parseInt(evt.target.value)},()=>this.refreshChartData())}>
                    <option key={NearlineStorageMembership.CHART_MODE_COUNT} value={NearlineStorageMembership.CHART_MODE_COUNT}>View by file count</option>
                    <option key={NearlineStorageMembership.CHART_MODE_SIZE} value={NearlineStorageMembership.CHART_MODE_SIZE}>View by file size</option>
                </select>
            </span>
            <div style={{width: "64vw", height:"800px",overflow:"hidden", display:"inline-block"}}>
                <Bar data={{
                    datasets: [{data:this.state.barData ? this.state.barData.datapoints : [], label: "Files with no membership"}],
                    labels: this.state.barData ? this.state.barData.labels : []
                }}
                     options={{
                         scales: {
                             yAxes: [{
                                 scaleLabel: {
                                     display: true,
                                     labelString: "File count",
                                 }
                             }],
                             xAxes: [{
                                 labelString: "Date",
                                 ticks: {
                                     callback: (value,index,series)=>{

                                         return moment(value).format("Do MMM YYYY")
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
            <div style={{width: "33vw",overflow:"hidden", float:"right"}}>
                <Pie data={{
                    datasets: [{data:this.state.pieData ? this.state.pieData.datapoints : []}],
                    labels: this.state.pieData ? this.state.pieData.labels : []
                }}
                     options={{
                         title: {
                             display: true,
                             label: "Files without item membership by file state",
                             fontSize: 24,
                             color: "#000000"
                         },
                         tooltips: {
                             callbacks: {
                                 label: (tooltipItem,data)=>{
                                     let xLabel=data.labels[tooltipItem.index];
                                     let yLabel=data.datasets[tooltipItem.datasetIndex].data[tooltipItem.index];

                                     console.log(tooltipItem);
                                     console.log(data);
                                     console.log(xLabel, yLabel);

                                     if(this.state.chartMode===NearlineStorageMembership.CHART_MODE_SIZE) {
                                         try {
                                             const result = BytesFormatterImplementation.getValueAndSuffix(yLabel);
                                             yLabel = result[0] + result[1];
                                             return xLabel + ": " + yLabel;
                                         } catch (err) {
                                             console.error(err);
                                             return xLabel + ": " + yLabel;
                                         }
                                     } else {
                                         return xLabel + ": " + yLabel;
                                     }
                                 }
                             }
                         },
                     }}

                />
            </div>
        </div>
    }
}

export default NearlineStorageMembership;