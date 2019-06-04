import React from 'react';
import PropTypes from 'prop-types';
import moment from 'moment';

class TimestampDiffComponent extends React.Component {
    static propTypes = {
        startTime: PropTypes.string.isRequired,
        endTime: PropTypes.string,
        formatString: PropTypes.string
    };

    render(){
        const formatToUse = this.props.formatString ? this.props.formatString : "";
        const startMoment = this.props.startTime ? moment(this.props.startTime) : moment();
        const endMoment = this.props.endTime ? moment(this.props.endTime) : moment();

        const out = endMoment.from(startMoment);
        return <span className="timestamp">{out}</span>
    }
}

export default TimestampDiffComponent;