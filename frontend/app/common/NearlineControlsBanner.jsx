import React from 'react';
import PropTypes from 'prop-types';
import RefreshButton from "./RefreshButton.jsx";
import {Link} from "react-router-dom";

class NearlineControlsBanner extends React.Component {

    static CHART_MODE_COUNT=1;
    static CHART_MODE_SIZE=2;

    static propTypes = {
        isRunning: PropTypes.bool.isRequired,
        dataModeChanged: PropTypes.func.isRequired,
        refreshClicked:PropTypes.func.isRequired,
        dataMode: PropTypes.number.isRequired
    };

    constructor(props){
        super(props);
    }

    render() {
        return <span className="controls-banner">
                <RefreshButton isRunning={this.props.isRunning} clickedCb={this.props.refreshClicked}/>
                <Link className="controls-banner-spacing" to="/nearlines">Nearline Stats</Link> |
                <Link className="controls-banner-spacing" to="/nearlines/membership">Nearline files without item membership</Link> |
                <select className="controls-banner-spacing" value={this.props.dataMode} onChange={this.props.dataModeChanged}>
                    <option key={NearlineControlsBanner.CHART_MODE_COUNT} value={NearlineControlsBanner.CHART_MODE_COUNT}>View by file count</option>
                    <option key={NearlineControlsBanner.CHART_MODE_SIZE} value={NearlineControlsBanner.CHART_MODE_SIZE}>View by file size</option>
                </select>
            </span>
    }
}

export default NearlineControlsBanner;