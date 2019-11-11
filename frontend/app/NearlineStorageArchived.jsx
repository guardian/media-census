import React from 'react';
import PropTypes from 'prop-types';
import {Pie,Bar,HorizontalBar} from "react-chartjs-2";
import NearlineControlsBanner from "./common/NearlineControlsBanner.jsx";
import BytesFormatterImplementation from "./common/BytesFormatterImplementation.jsx";
import {FontAwesomeIcon} from "@fortawesome/react-fontawesome";
import TimestampDiffComponent from "./common/TimestampDiffComponent.jsx";
import moment from 'moment';

//see https://stackoverflow.com/questions/2901102/how-to-print-a-number-with-commas-as-thousands-separators-in-javascript
function numberWithCommas(x) {
    return x.toString().replace(/\B(?=(\d{3})+(?!\d))/g, ",");
}

class NearlineStorageArchived extends React.Component {
    constructor(props){
        super(props);

        this.state = {
            loading: false,
            lastError: null,
            not_archived_totalsize: 0,
            in_archive_totalsize: 0,
            not_archived_count: 0,
            in_archive_count: 0,
            total_size: 0,
            total_count: 0,
            scannerCurrentJobStart: null,
            scannerLastRunFinish: null,
            scannerLastRunError: null,
            dataMode: NearlineControlsBanner.CHART_MODE_COUNT
        };

        this.loadData = this.loadData.bind(this);
        this.changeDataMode = this.changeDataMode.bind(this);
    }

    setStatePromise(newState){
        return new Promise((resolve, reject)=>{
            try {
                this.setState(newState, () => resolve())
            } catch(except) {
                console.error(except);
                reject(except);
            }
        }
        )
    }

    async loadData() {
        await this.setStatePromise({loading: true});

        try {
            const responses = await Promise.all([fetch("/api/nearline/archivedStats"), fetch("/api/jobs/ArchiveHunterScan/lastSuccess?includeRunning=true")]);
            const returnedJsons = await Promise.all(responses.map(rsp=>rsp.json()));

            return this.setStatePromise({loading: false,
                lastError: null,
                not_archived_totalsize: returnedJsons[0].missing_archive_id_totalsize,
                in_archive_totalsize: returnedJsons[0].with_archive_id_totalsize,
                not_archived_count: returnedJsons[0].missing_archive_id_count,
                in_archive_count: returnedJsons[0].with_archive_id_count,
                total_count: returnedJsons[0].total_count,
                total_size: returnedJsons[0].total_size,
                scannerCurrentJobStart: returnedJsons[1].entry[1] ? returnedJsons[1].entry[1].scanStart : null,
                scannerLastRunFinish: returnedJsons[1].entry[0] ? returnedJsons[1].entry[0].scanFinish : null,
                scannerLastRunError: returnedJsons[1].entry[0] ? returnedJsons[1].entry[0].lastError : null
            })
        } catch (except){
            console.error(except);
            return this.setStatePromise({
                loading: false,
                lastError: except
            })
        }
    }

    componentDidMount() {
        this.loadData();
    }

    changeDataMode(evt){
        console.log("Changing data mode to " + evt.target.value);
        this.setState({dataMode: parseInt(evt.target.value)});
    }

    static makeColourValues(count, offset){
        let values = [];
        for(let n=0;n<count;++n){
            let hue = (n/count)*360.0 + offset;
            values[n] = 'hsla(' + hue + ',75%,50%,0.6)'
        }
        return values;
    }

    makePieData(){
        const colours = NearlineStorageArchived.makeColourValues(4,0);

        switch(this.state.dataMode){
            case NearlineControlsBanner.CHART_MODE_COUNT:
                return {
                    data: [this.state.in_archive_count, this.state.not_archived_count],
                    labels: ["In archive and nearline","Nearline only"],
                    backgroundColor: [colours[0],colours[1]]
                };
            case NearlineControlsBanner.CHART_MODE_SIZE:
                return {
                    data: [this.state.in_archive_totalsize, this.state.not_archived_totalsize],
                    labels: ["Data in archive and nearline", "Data in nearline only"],
                    backgroundColor: [colours[0],colours[1]]
                };
            default:
                return {
                    data: [0],
                    labels: ["Invalid mode, this is a code bug"]
                }
        }
    }

    renderTotalsMetric(){
        switch(this.state.dataMode){
            case NearlineControlsBanner.CHART_MODE_COUNT:
                return <span><FontAwesomeIcon icon="info-circle" color="navy" className="bullet-point-icon"/>There are currently <b>{numberWithCommas(this.state.total_count)}</b> items on the nearlines</span>;
            case NearlineControlsBanner.CHART_MODE_SIZE:
                const bytesValueAndSuffix = BytesFormatterImplementation.getValueAndSuffix(this.state.total_size);
                return <span><FontAwesomeIcon icon="info-circle" color="navy" className="bullet-point-icon"/>Total size of files on nearlines: <b>{bytesValueAndSuffix[0]} {bytesValueAndSuffix[1]}</b></span>
        }
    }

    renderLastSuccessMessage(){
        if(this.state.scannerLastRunFinish){
            if(this.state.scannerLastRunError){
                return <span><FontAwesomeIcon icon="exclamation-triangle" className="bullet-point-icon" style={{color: "red"}}/>The last scanner run failed at {this.state.scannerLastRunFinish}, results may be incomplete</span>
            } else {
                return <span><FontAwesomeIcon icon="check" className="bullet-point-icon" style={{color: "green"}}/>The scanner last completed successfully <TimestampDiffComponent startTime={this.state.scannerLastRunFinish}/> ago</span>
            }
        } else {
            return <span><FontAwesomeIcon icon="exclamation-triangle" className="bullet-point-icon" style={{color: "orange"}}/>The scanner has not yet completed successfully</span>
        }
    }

    renderCurrentRunMessage(){
        if(this.state.scannerCurrentJobStart && moment(this.state.scannerCurrentJobStart).isAfter(moment(this.state.scannerLastRunFinish))){
            return <span><FontAwesomeIcon icon="exclamation-triangle" className="bullet-point-icon" style={{color: "orange"}}/>The scanner has been running for <TimestampDiffComponent startTime={this.state.scannerCurrentJobStart}/>, the given results may change</span>
        } else {
            return <span><FontAwesomeIcon icon="check" className="bullet-point-icon" style={{color: "green"}}/>The scanner is not currently running</span>
        }
    }

    render(){
        const pieData = this.makePieData();

        return <div className="container">
            <NearlineControlsBanner isRunning={this.state.loading}
                                    dataModeChanged={this.changeDataMode}
                                    refreshClicked={this.loadData}
                                    dataMode={this.state.dataMode}/>
            <div style={{display: "block", float:"left", marginBottom:"auto"}}>
                <ul className="no-bullets" style={{marginLeft:"1em"}}>
                    <li style={{listStyle: "none"}}>{this.renderLastSuccessMessage()}</li>
                    <li style={{listStyle: "none"}}>{this.renderCurrentRunMessage()}</li>
                    <li style={{listStyle: "none"}}>{this.renderTotalsMetric()}</li>
                </ul>
            </div>
            <div style={{width: "600px", display:"inline-block",overflow:"hidden"}}>
                <Pie
                    data={{
                    datasets: [{data: pieData.data, backgroundColor: pieData.backgroundColor}],
                    labels: pieData.labels,

                }}
                    options={{
                        title: {
                            display: true,
                            label: "Nearline-Archive crossover",
                            fontSize: 24,
                            color: "#000000"
                        },
                        tooltips: {
                            callbacks: {
                                label: (tooltipItem, data)=>{
                                    const xLabel = data.labels[tooltipItem.index];
                                    const rawYValue = data.datasets[0].data[tooltipItem.index];

                                    switch(this.state.dataMode){
                                        case NearlineControlsBanner.CHART_MODE_SIZE:
                                            const bytesValueAndSuffix = BytesFormatterImplementation.getValueAndSuffix(rawYValue);
                                            return xLabel + ": " + bytesValueAndSuffix[0] + bytesValueAndSuffix[1];
                                        case NearlineControlsBanner.CHART_MODE_COUNT:
                                            return xLabel + ": " + numberWithCommas(rawYValue);
                                        default:
                                            return "invalid mode";
                                    }
                                }
                            }
                        },
                        legend: {
                            display: true,
                            position: "bottom"
                        }
                    }}
                     redraw={true}
                />
             </div>
        </div>
    }
}

export default NearlineStorageArchived;